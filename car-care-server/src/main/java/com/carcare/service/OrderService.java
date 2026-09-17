package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BaseContext;
import com.carcare.common.BusinessException;
import com.carcare.dto.OrderCreateDTO;
import com.carcare.dto.PageQuery;
import com.carcare.entity.*;
import com.carcare.mapper.*;
import com.carcare.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单服务：列表查询（冗余名称回填）+ 状态机流转 + 工单联动。
 * 订单状态机（TRANSITIONS）定义合法流转，非法跳转一律拒绝：
 * 1待支付 → {2已支付, 5已取消}；2 → {3施工中}；3 → {4已完工}；4 → {6已评价}
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 状态流转表：当前状态 -> 允许到达的目标状态集合 */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            1, Set.of(2, 5),   // 待支付 -> 已支付 / 已取消
            2, Set.of(3),      // 已支付 -> 施工中
            3, Set.of(4),      // 施工中 -> 已完工
            4, Set.of(6)       // 已完工 -> 已评价
    );

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final StoreMapper storeMapper;
    private final UserMapper userMapper;
    private final VehicleMapper vehicleMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ServicePackageMapper packageMapper;
    private final ServiceItemMapper itemMapper;
    private final PackageItemMapper packageItemMapper;
    private final SegmentService segmentService;
    private final WsNotifyService wsNotifyService;

    /**
     * 用户端下单（阶段2b）：创建待支付订单 + 明细快照，
     * 返回订单 id 后由调用方投递延迟关单消息（事务提交后再发，避免消息先于数据可见）。
     * 订单号 = 业务前缀 CC + 日期 + 号段id：趋势递增、可按日期前缀检索、不暴露总量
     */
    @Transactional
    public Order createOrder(OrderCreateDTO dto) {
        Long userId = BaseContext.getUserId();
        Store store = storeMapper.selectById(dto.getStoreId());
        if (store == null || store.getStatus() == 0) {
            throw new BusinessException("门店不存在或休息中");
        }
        Order order = new Order();
        order.setOrderNo("CC" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + segmentService.nextId("order"));
        order.setUserId(userId);
        order.setStoreId(dto.getStoreId());
        order.setVehicleId(dto.getVehicleId());
        order.setPackageId(dto.getPackageId());
        order.setRemark(dto.getRemark());
        order.setAppointmentTime(dto.getAppointmentTime());
        order.setStatus(1);
        order.setPayStatus(0);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setOrderTime(LocalDateTime.now());

        BigDecimal amount;
        OrderDetail detail = new OrderDetail();
        if (dto.getPackageId() != null) {
            ServicePackage pkg = packageMapper.selectById(dto.getPackageId());
            if (pkg == null || pkg.getStatus() == 0) {
                throw new BusinessException("套餐不存在或已下架");
            }
            amount = pkg.getPrice();
            detail.setItemName(pkg.getName());
            detail.setUnitPrice(pkg.getPrice());
        } else if (dto.getItemId() != null) {
            ServiceItem item = itemMapper.selectById(dto.getItemId());
            if (item == null || item.getStatus() == 0) {
                throw new BusinessException("服务项目不存在或已停售");
            }
            int number = dto.getNumber() == null ? 1 : dto.getNumber();
            amount = item.getPrice().multiply(BigDecimal.valueOf(number));
            detail.setItemId(item.getId());
            detail.setItemName(item.getName());
            detail.setUnitPrice(item.getPrice());
            detail.setNumber(number);
        } else {
            throw new BusinessException("套餐与服务项目至少选择一项");
        }
        detail.setAmount(amount);
        order.setTotalAmount(amount);
        order.setActualAmount(amount);
        orderMapper.insert(order);
        detail.setOrderId(order.getId());
        orderDetailMapper.insert(detail);
        // 来单提醒：通知发布在事务提交后由监听器触发更严谨，
        // 演示场景前端仅弹提示不立即回查，接受毫秒级竞态
        Store storeForNotify = storeMapper.selectById(order.getStoreId());
        wsNotifyService.notifyAdmins("order", "新订单 " + order.getOrderNo()
                + "（" + (storeForNotify == null ? "" : storeForNotify.getName()) + "）¥" + amount,
                Map.of("orderId", order.getId(), "orderNo", order.getOrderNo()));
        return order;
    }

    /**
     * 订单分页并回填展示字段：先查本页订单，再按关联 id 批量查门店/用户/车辆
     * 拼成内存 Map 回填，避免 N+1 逐条查询
     */
    public Page<OrderVO> page(PageQuery query) {
        Page<Order> page = orderMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<Order>lambdaQuery()
                        .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                        .eq(query.getStoreId() != null, Order::getStoreId, query.getStoreId())
                        .like(StringUtils.hasText(query.getName()), Order::getOrderNo, query.getName())
                        .orderByDesc(Order::getId));
        Page<OrderVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            return voPage;
        }
        Set<Long> storeIds = page.getRecords().stream().map(Order::getStoreId).collect(Collectors.toSet());
        Set<Long> userIds = page.getRecords().stream().map(Order::getUserId).collect(Collectors.toSet());
        Map<Long, Store> storeMap = storeMapper.selectByIds(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<Long, User> userMap = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> vehicleIds = page.getRecords().stream()
                .map(Order::getVehicleId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Vehicle> vehicleMap = vehicleIds.isEmpty() ? Map.of()
                : vehicleMapper.selectByIds(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        voPage.setRecords(page.getRecords().stream().map(o -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(o, vo);
            Store store = storeMap.get(o.getStoreId());
            User user = userMap.get(o.getUserId());
            Vehicle vehicle = o.getVehicleId() == null ? null : vehicleMap.get(o.getVehicleId());
            vo.setStoreName(store == null ? "" : store.getName());
            vo.setUserName(user == null ? "" : user.getName());
            vo.setPlateNumber(vehicle == null ? "" : vehicle.getPlateNumber());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    /** 订单详情：主单 + 明细 + 工单（未生成工单时返回空对象，前端自行判空） */
    public Map<String, Object> detail(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        List<OrderDetail> details = orderDetailMapper.selectList(Wrappers.<OrderDetail>lambdaQuery()
                .eq(OrderDetail::getOrderId, id));
        WorkOrder workOrder = workOrderMapper.selectOne(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getOrderId, id));
        return Map.of("order", order, "details", details,
                "workOrder", workOrder == null ? new WorkOrder() : workOrder);
    }

    /** 用户端「我的订单」：按用户过滤的分页 */
    public Page<OrderVO> pageByUser(Long userId, Integer pageNum, Integer pageSize) {
        PageQuery q = new PageQuery();
        q.setPageNum(pageNum);
        q.setPageSize(pageSize);
        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Order>lambdaQuery().eq(Order::getUserId, userId).orderByDesc(Order::getId));
        Page<OrderVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            return voPage;
        }
        Set<Long> storeIds = page.getRecords().stream().map(Order::getStoreId).collect(Collectors.toSet());
        Map<Long, Store> storeMap = storeMapper.selectByIds(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        voPage.setRecords(page.getRecords().stream().map(o -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(o, vo);
            Store store = storeMap.get(o.getStoreId());
            vo.setStoreName(store == null ? "" : store.getName());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    /** 车主取消自己的订单：先校验归属，再走状态机 1→5 */
    public void cancelOwn(Long orderId, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(BaseContext.getUserId())) {
            throw new BusinessException("无权操作该订单");
        }
        changeStatus(orderId, 5, reason);
    }

    /**
     * 支付回调置已支付：按商户订单号（orderNo）查询，
     * 已支付则直接忽略（幂等），重复回调不重复扣状态
     */
    @Transactional
    public void paySuccessByOrderNo(String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException("订单不存在：" + orderNo);
        }
        if (order.getStatus() == 1) {
            changeStatus(order.getId(), 2, null);
        }
    }

    /**
     * 状态机流转入口。副作用按时序触发：
     * 支付→记录支付时间；施工→生成维修工单；完工→工单收单；取消→留存取消原因
     */
    @Transactional
    public void changeStatus(Long orderId, Integer targetStatus, String cancelReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Integer current = order.getStatus();
        Set<Integer> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new BusinessException("订单状态不允许从 " + current + " 流转到 " + targetStatus);
        }
        order.setStatus(targetStatus);
        switch (targetStatus) {
            case 2 -> {
                order.setPayStatus(1);
                order.setCheckoutTime(LocalDateTime.now());
            }
            case 3 -> ensureWorkOrder(order);
            case 4 -> finishWorkOrder(orderId);
            case 5 -> {
                order.setCancelReason(cancelReason);
                order.setCancelTime(LocalDateTime.now());
            }
            default -> { }
        }
        orderMapper.updateById(order);
    }

    /** 进入施工时若尚无工单则自动创建，初始状态为“维修中” */
    private void ensureWorkOrder(Order order) {
        WorkOrder exist = workOrderMapper.selectOne(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getOrderId, order.getId()));
        if (exist == null) {
            WorkOrder wo = new WorkOrder();
            wo.setOrderId(order.getId());
            wo.setStoreId(order.getStoreId());
            wo.setStatus(2);
            wo.setProgressDesc("订单已支付，进入施工队列");
            wo.setStartTime(LocalDateTime.now());
            workOrderMapper.insert(wo);
            wsNotifyService.notifyAdmins("work", "工单已生成：订单 " + order.getOrderNo() + " 待接单施工",
                    Map.of("orderId", order.getId(), "workOrderId", wo.getId()));
        }
    }

    /** 订单完工同步收结对应工单 */
    private void finishWorkOrder(Long orderId) {
        WorkOrder wo = workOrderMapper.selectOne(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getOrderId, orderId));
        if (wo != null) {
            wo.setStatus(4);
            wo.setProgressDesc("维修完工，等待车主确认");
            wo.setFinishTime(LocalDateTime.now());
            workOrderMapper.updateById(wo);
        }
    }
}
