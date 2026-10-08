package com.carcare.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.entity.Coupon;
import com.carcare.entity.PackageItem;
import com.carcare.entity.ServiceItem;
import com.carcare.entity.ServicePackage;
import com.carcare.entity.Store;
import com.carcare.mapper.CouponMapper;
import com.carcare.mapper.PackageItemMapper;
import com.carcare.mapper.ServiceItemMapper;
import com.carcare.mapper.ServicePackageMapper;
import com.carcare.vo.AiPackageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 服务的只读数据通道（/api/internal/ai/**）。
 * <p>
 * 为什么不直接把这些查询暴露成通用 SQL 网关：这里只提供**固定语义的只读查询**，
 * 过滤口径（营业中/在售/启用/有效期）在 Java 侧收敛成一份实现，
 * AI 服务无法构造任意查询，也就不存在「通过 AI 通道拖库」的风险。
 * <p>
 * 门店列表直接复用 {@link StoreService#listEnabled()}，因此顺带继承了阶段1的
 * 缓存击穿/穿透防护——AI 侧的高频查询不会打到 MySQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInternalService {

    private final StoreService storeService;
    private final ServiceItemMapper itemMapper;
    private final ServicePackageMapper packageMapper;
    private final PackageItemMapper packageItemMapper;
    private final CouponMapper couponMapper;

    /** 营业中门店：按城市/关键字模糊匹配。数据来自门店列表缓存，不打 DB */
    public List<Store> stores(String city, String keyword, int limit) {
        List<Store> enabled = storeService.listEnabled();
        return enabled.stream()
                .filter(s -> matchesCity(s, city))
                .filter(s -> matchesKeyword(s, keyword))
                .limit(limit)
                .toList();
    }

    private boolean matchesCity(Store store, String city) {
        if (!StringUtils.hasText(city)) {
            return true;
        }
        if (city.equals(store.getCity())) {
            return true;
        }
        // 用户说的「杭州」可能落在地址里而不是 city 字段，宽松匹配一次
        return store.getAddress() != null && store.getAddress().contains(city);
    }

    private boolean matchesKeyword(Store store, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return (store.getName() != null && store.getName().contains(keyword))
                || (store.getAddress() != null && store.getAddress().contains(keyword));
    }

    /** 在售服务项目：只返回 status=1，与车主端浏览口径一致 */
    public List<ServiceItem> items(Long storeId, String keyword, int limit) {
        return itemMapper.selectPage(new Page<>(1, limit),
                Wrappers.<ServiceItem>lambdaQuery()
                        .eq(ServiceItem::getStatus, 1)
                        .eq(storeId != null, ServiceItem::getStoreId, storeId)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(ServiceItem::getName, keyword)
                                .or().like(ServiceItem::getDescription, keyword))
                        .orderByAsc(ServiceItem::getId)).getRecords();
    }

    /** 启用中的套餐，附拍平后的项目名 */
    public List<AiPackageVO> packages(Long storeId, int limit) {
        List<ServicePackage> packages = packageMapper.selectPage(new Page<>(1, limit),
                Wrappers.<ServicePackage>lambdaQuery()
                        .eq(ServicePackage::getStatus, 1)
                        .eq(storeId != null, ServicePackage::getStoreId, storeId)
                        .orderByAsc(ServicePackage::getId)).getRecords();
        if (packages.isEmpty()) {
            return List.of();
        }
        // 一次性把明细查出来再分组，避免套餐数 × 明细查询的 N+1
        List<Long> packageIds = packages.stream().map(ServicePackage::getId).toList();
        Map<Long, List<PackageItem>> itemsByPackage = packageItemMapper.selectList(
                        Wrappers.<PackageItem>lambdaQuery().in(PackageItem::getPackageId, packageIds))
                .stream().collect(Collectors.groupingBy(PackageItem::getPackageId));

        List<AiPackageVO> result = new ArrayList<>(packages.size());
        for (ServicePackage pkg : packages) {
            AiPackageVO vo = new AiPackageVO();
            vo.setId(pkg.getId());
            vo.setStoreId(pkg.getStoreId());
            vo.setName(pkg.getName());
            vo.setPrice(pkg.getPrice());
            vo.setDescription(pkg.getDescription());
            vo.setItemNames(itemsByPackage.getOrDefault(pkg.getId(), List.of()).stream()
                    .map(this::itemNameOf)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            result.add(vo);
        }
        return result;
    }

    /** 从套餐明细的 JSON 快照里取项目名；快照结构异常时跳过而不是让整个查询失败 */
    private String itemNameOf(PackageItem item) {
        try {
            return JSONUtil.parseObj(item.getItemData()).getStr("name");
        } catch (Exception e) {
            log.warn("套餐明细快照解析失败 packageId={} itemData={}", item.getPackageId(), item.getItemData());
            return null;
        }
    }

    /** 可抢优惠券：有效期内且有库存。这是平台公开的券池，不含用户领取记录 */
    public List<Coupon> claimableCoupons(Long storeId, int limit) {
        return couponMapper.selectPage(new Page<>(1, limit),
                Wrappers.<Coupon>lambdaQuery()
                        .eq(storeId != null, Coupon::getStoreId, storeId)
                        .gt(Coupon::getStock, 0)
                        .gt(Coupon::getValidEndTime, LocalDateTime.now())
                        .orderByAsc(Coupon::getMinPrice)).getRecords();
    }

    /** 用户已领取且在有效期内的券，供组装下发上下文使用 */
    public List<Coupon> couponsByIds(List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Coupon> byId = couponMapper.selectByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity(), (a, b) -> a));
        return couponIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
