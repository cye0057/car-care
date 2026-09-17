package com.carcare.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.common.BaseContext;
import com.carcare.common.Result;
import com.carcare.entity.Coupon;
import com.carcare.entity.CouponOrder;
import com.carcare.mapper.CouponMapper;
import com.carcare.mapper.CouponOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车主侧优惠券接口：可抢券列表、我的券。
 * 车辆档案已独立到 VehicleController（/api/vehicles）
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "16-车主优惠券", description = "可抢券/我的券")
public class CouponUserController {

    private final CouponMapper couponMapper;
    private final CouponOrderMapper couponOrderMapper;

    @GetMapping("/api/coupons/active")
    @Operation(summary = "可抢优惠券列表", description = "有效期内且有库存；库存余量走 Redis 预减视图")
    public Result<List<Coupon>> active(@RequestParam(required = false) Long storeId) {
        return Result.success(couponMapper.selectList(Wrappers.<Coupon>lambdaQuery()
                .eq(storeId != null, Coupon::getStoreId, storeId)
                .gt(Coupon::getStock, 0)
                .gt(Coupon::getValidEndTime, LocalDateTime.now())));
    }

    @GetMapping("/api/coupons/my")
    @Operation(summary = "我的优惠券")
    public Result<List<CouponOrder>> my() {
        return Result.success(couponOrderMapper.selectList(Wrappers.<CouponOrder>lambdaQuery()
                .eq(CouponOrder::getUserId, BaseContext.getUserId())
                .orderByDesc(CouponOrder::getId)));
    }
}
