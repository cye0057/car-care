package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BusinessException;
import com.carcare.dto.PageQuery;
import com.carcare.entity.Coupon;
import com.carcare.mapper.CouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 优惠券管理服务（后台配置侧）。
 * 本阶段只做券的定义与维护；用户抢券/用券链路在阶段2 实现，
 * 届时 stock 扣减将迁移到 Redis+Lua 预减库存，此处仅保留普通 CRUD
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;

    public Page<Coupon> page(PageQuery query) {
        return couponMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<Coupon>lambdaQuery()
                        .eq(query.getStoreId() != null, Coupon::getStoreId, query.getStoreId())
                        .like(StringUtils.hasText(query.getName()), Coupon::getTitle, query.getName())
                        .orderByDesc(Coupon::getId));
    }

    public Coupon getById(Long id) {
        return couponMapper.selectById(id);
    }

    /** 新建券：type=1 满减券（看 minPrice/discountPrice），type=2 代金券（看 cashPrice） */
    public void save(Coupon coupon) {
        couponMapper.insert(coupon);
    }

    public void update(Coupon coupon) {
        Coupon db = couponMapper.selectById(coupon.getId());
        if (db == null) {
            throw new BusinessException("优惠券不存在");
        }
        couponMapper.updateById(coupon);
    }

    public void delete(Long id) {
        couponMapper.deleteById(id);
    }
}
