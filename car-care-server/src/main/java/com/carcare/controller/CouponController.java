package com.carcare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.Result;
import com.carcare.dto.PageQuery;
import com.carcare.entity.Coupon;
import com.carcare.service.CouponService;
import com.carcare.service.VoucherSeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券管理接口：后台配置券模板（满减/代金/库存/有效期）；
 * 用户抢券接口在阶段2（Redis+Lua+MQ）实现
 */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "06-优惠券管理", description = "券模板配置")
public class CouponController {

    private final CouponService couponService;
    private final VoucherSeckillService seckillService;

    @GetMapping
    @Operation(summary = "优惠券分页查询")
    public Result<Page<Coupon>> page(PageQuery query) {
        return Result.success(couponService.page(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "优惠券详情")
    public Result<Coupon> detail(@PathVariable Long id) {
        return Result.success(couponService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增优惠券", description = "type=1 满减券，type=2 代金券")
    public Result<Void> save(@RequestBody Coupon coupon) {
        couponService.save(coupon);
        return Result.success();
    }

    @PutMapping
    @Operation(summary = "修改优惠券")
    public Result<Void> update(@RequestBody Coupon coupon) {
        couponService.update(coupon);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除优惠券")
    public Result<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/seckill-publish")
    @Operation(summary = "发布/重置秒杀", description = "以 DB 库存为准重建 Redis 预减库存并清空领取名单，压测重置现场用")
    public Result<Void> publishSeckill(@PathVariable Long id) {
        seckillService.publish(id);
        return Result.success();
    }
}
