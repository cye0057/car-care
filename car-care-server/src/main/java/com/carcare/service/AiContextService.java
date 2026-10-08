package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.config.AiProperties;
import com.carcare.entity.Coupon;
import com.carcare.entity.CouponOrder;
import com.carcare.entity.Order;
import com.carcare.entity.OrderDetail;
import com.carcare.entity.Store;
import com.carcare.entity.User;
import com.carcare.entity.Vehicle;
import com.carcare.entity.WorkOrder;
import com.carcare.mapper.CouponOrderMapper;
import com.carcare.mapper.OrderDetailMapper;
import com.carcare.mapper.OrderMapper;
import com.carcare.mapper.StoreMapper;
import com.carcare.mapper.UserMapper;
import com.carcare.mapper.VehicleMapper;
import com.carcare.mapper.WorkOrderMapper;
import com.carcare.vo.AiUserContextVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组装下发给 AI 服务的用户上下文。
 * <p>
 * 这是「AI 服务不持有用户态」的实现点：调用方传入的 userId 来自已通过 JWT 校验的
 * BaseContext，这里只做「按这个 userId 取数据并裁剪」，不接受任何来自请求体的 userId。
 * 因此车主无法通过改请求参数让 AI 读到别人的订单——越权在结构上就不成立。
 * <p>
 * 裁剪是有意的：车辆 5 台、订单 5 笔、每笔 3 个明细、券 10 张。
 * 全量下发会让 prompt 膨胀到几万 token，既慢又贵，而模型实际只需要「近期上下文」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextService {

    /** 订单状态中文名，与 OrderService 的状态机 1→5/2→3/3→4/4→6 对应 */
    private static final Map<Integer, String> ORDER_STATUS_TEXT = Map.of(
            1, "待支付", 2, "已支付", 3, "施工中", 4, "已完工", 5, "已取消", 6, "已评价");

    /** 工单状态中文名 */
    private static final Map<Integer, String> WORK_ORDER_STATUS_TEXT = Map.of(
            1, "待接单", 2, "维修中", 3, "待验收", 4, "已完工");

    private static final Map<Integer, String> COUPON_TYPE_TEXT = Map.of(1, "满减券", 2, "代金券");

    /** 时间统一格式化成字符串下发：契约明确、模型易读，也绕开 Jackson 时间序列化的配置差异 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AiProperties aiProperties;
    private final UserMapper userMapper;
    private final VehicleMapper vehicleMapper;
    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final WorkOrderMapper workOrderMapper;
    private final StoreMapper storeMapper;
    private final CouponOrderMapper couponOrderMapper;
    private final AiInternalService aiInternalService;

    /**
     * 组装上下文。任何一步失败都不抛异常——上下文缺失只会让 AI 少知道一些事，
     * 但不该让整次对话失败（车主还是能问保养知识）。
     */
    public AiUserContextVO build(Long userId) {
        AiUserContextVO context = new AiUserContextVO();
        context.setUserId(userId);
        context.setVehicles(List.of());
        context.setRecentOrders(List.of());
        context.setWorkOrders(List.of());
        context.setCoupons(List.of());
        if (userId == null) {
            return context;
        }
        try {
            User user = userMapper.selectById(userId);
            if (user != null) {
                context.setName(user.getName());
            }
            context.setVehicles(loadVehicles(userId));
            List<Order> orders = loadRecentOrders(userId);
            context.setRecentOrders(loadOrderViews(orders));
            context.setWorkOrders(loadWorkOrders(orders));
            context.setCoupons(loadCoupons(userId));
            context.setCity(resolveCity(orders));
        } catch (Exception e) {
            log.warn("组装 AI 用户上下文失败 userId={}，按空上下文继续", userId, e);
        }
        return context;
    }

    /** 车辆档案：字段直接对齐，不冗余车牌以外的敏感信息 */
    private List<AiUserContextVO.Vehicle> loadVehicles(Long userId) {
        return vehicleMapper.selectPage(new Page<>(1, aiProperties.getContextVehicleLimit()),
                        Wrappers.<Vehicle>lambdaQuery()
                                .eq(Vehicle::getUserId, userId)
                                .orderByDesc(Vehicle::getId)).getRecords().stream()
                .map(v -> {
                    AiUserContextVO.Vehicle vo = new AiUserContextVO.Vehicle();
                    vo.setId(v.getId());
                    vo.setPlateNumber(v.getPlateNumber());
                    vo.setBrand(v.getBrand());
                    vo.setModel(v.getModel());
                    vo.setColor(v.getColor());
                    vo.setMileage(v.getMileage());
                    vo.setRegisterDate(fmtDate(v.getRegisterDate()));
                    vo.setNextMaintainDate(fmtDate(v.getNextMaintainDate()));
                    return vo;
                }).collect(Collectors.toList());
    }

    private List<Order> loadRecentOrders(Long userId) {
        return orderMapper.selectPage(new Page<>(1, aiProperties.getContextOrderLimit()),
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getId)).getRecords();
    }

    /** 把订单实体转成精简视图，批量补齐门店名与明细，避免逐条查询 */
    private List<AiUserContextVO.Order> loadOrderViews(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderDetail>> detailsByOrder = orderDetailMapper.selectList(
                        Wrappers.<OrderDetail>lambdaQuery().in(OrderDetail::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(OrderDetail::getOrderId));

        List<Long> storeIds = orders.stream().map(Order::getStoreId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Store> storeById = storeIds.isEmpty() ? Map.of()
                : storeMapper.selectByIds(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity(), (a, b) -> a));

        List<AiUserContextVO.Order> views = new ArrayList<>(orders.size());
        for (Order order : orders) {
            AiUserContextVO.Order vo = new AiUserContextVO.Order();
            vo.setOrderNo(order.getOrderNo());
            vo.setStoreId(order.getStoreId());
            Store store = order.getStoreId() == null ? null : storeById.get(order.getStoreId());
            vo.setStoreName(store == null ? "" : store.getName());
            vo.setStatus(order.getStatus());
            vo.setStatusText(ORDER_STATUS_TEXT.getOrDefault(order.getStatus(), "未知状态"));
            vo.setTotalAmount(order.getTotalAmount());
            vo.setActualAmount(order.getActualAmount());
            vo.setAppointmentTime(fmtDateTime(order.getAppointmentTime()));
            vo.setOrderTime(fmtDateTime(order.getOrderTime()));
            // 明细只带项目名和金额：模型不需要单价和图片，少传一点省 token
            vo.setItems(detailsByOrder.getOrDefault(order.getId(), List.of()).stream()
                    .limit(aiProperties.getContextOrderItemLimit())
                    .map(d -> {
                        AiUserContextVO.OrderItem item = new AiUserContextVO.OrderItem();
                        item.setItemName(d.getItemName());
                        item.setNumber(d.getNumber());
                        item.setAmount(d.getAmount());
                        return item;
                    }).collect(Collectors.toList()));
            views.add(vo);
        }
        return views;
    }

    /** 工单进度：只取这批订单关联的工单，并回填订单号（模型认订单号，不认内部 orderId） */
    private List<AiUserContextVO.WorkOrder> loadWorkOrders(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, String> orderNoById = orders.stream()
                .collect(Collectors.toMap(Order::getId, Order::getOrderNo, (a, b) -> a));
        List<WorkOrder> workOrders = workOrderMapper.selectList(Wrappers.<WorkOrder>lambdaQuery()
                .in(WorkOrder::getOrderId, orderNoById.keySet()));
        List<AiUserContextVO.WorkOrder> views = new ArrayList<>(workOrders.size());
        for (WorkOrder wo : workOrders) {
            AiUserContextVO.WorkOrder vo = new AiUserContextVO.WorkOrder();
            vo.setOrderNo(orderNoById.get(wo.getOrderId()));
            vo.setStoreId(wo.getStoreId());
            vo.setStatus(wo.getStatus());
            vo.setStatusText(WORK_ORDER_STATUS_TEXT.getOrDefault(wo.getStatus(), "未知状态"));
            vo.setTechnician(wo.getTechnician());
            vo.setProgressDesc(wo.getProgressDesc());
            vo.setStartTime(fmtDateTime(wo.getStartTime()));
            vo.setFinishTime(fmtDateTime(wo.getFinishTime()));
            views.add(vo);
        }
        return views;
    }

    /** 已领取且未使用、未过期的券。状态码沿用 t_coupon_order：1未用 2已用 3过期 */
    private List<AiUserContextVO.Coupon> loadCoupons(Long userId) {
        List<CouponOrder> owned = couponOrderMapper.selectPage(
                new Page<>(1, aiProperties.getContextCouponLimit()),
                Wrappers.<CouponOrder>lambdaQuery()
                        .eq(CouponOrder::getUserId, userId)
                        .eq(CouponOrder::getStatus, 1)
                        .orderByDesc(CouponOrder::getId)).getRecords();
        if (owned.isEmpty()) {
            return List.of();
        }
        Map<Long, Coupon> couponById = aiInternalService
                .couponsByIds(owned.stream().map(CouponOrder::getCouponId).distinct().toList())
                .stream().collect(Collectors.toMap(Coupon::getId, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<AiUserContextVO.Coupon> views = new ArrayList<>();
        for (CouponOrder co : owned) {
            Coupon coupon = couponById.get(co.getCouponId());
            if (coupon == null) {
                continue;
            }
            // 以用户领取记录上的有效期为准（券本身可能已下架，但已领的仍可用到 co.endTime）
            LocalDateTime expireAt = co.getEndTime() != null ? co.getEndTime() : coupon.getValidEndTime();
            if (expireAt != null && expireAt.isBefore(now)) {
                continue;
            }
            AiUserContextVO.Coupon vo = new AiUserContextVO.Coupon();
            vo.setCouponId(coupon.getId());
            vo.setStoreId(coupon.getStoreId());
            vo.setTitle(coupon.getTitle());
            vo.setType(coupon.getType());
            vo.setTypeDesc(COUPON_TYPE_TEXT.getOrDefault(coupon.getType(), "优惠券"));
            vo.setMinPrice(coupon.getMinPrice());
            vo.setDiscountPrice(coupon.getDiscountPrice());
            vo.setCashPrice(coupon.getCashPrice());
            vo.setValidEndTime(fmtDateTime(expireAt));
            views.add(vo);
        }
        return views;
    }

    /**
     * 推断用户所在城市：取最近一笔订单的门店所在城市。
     * 用户表没有城市字段，与其新加一列，不如用这个已有信号——车主最常去的门店城市
     * 基本就是他的活动城市，用来做门店推荐的默认范围足够准。
     */
    private String resolveCity(List<Order> orders) {
        List<Long> storeIds = orders.stream()
                .map(Order::getStoreId).filter(Objects::nonNull).distinct().toList();
        if (storeIds.isEmpty()) {
            return null;
        }
        Map<Long, Store> byId = new HashMap<>();
        storeMapper.selectByIds(storeIds).forEach(s -> byId.put(s.getId(), s));
        for (Order order : orders) {
            Store store = order.getStoreId() == null ? null : byId.get(order.getStoreId());
            if (store != null && store.getCity() != null && !store.getCity().isBlank()) {
                return store.getCity();
            }
        }
        return null;
    }

    /** 格式化日期；null 保持 null，让 AI 看到「未记录」而不是一个假日期 */
    private static String fmtDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FMT);
    }

    private static String fmtDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATETIME_FMT);
    }
}
