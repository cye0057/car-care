package com.carcare.controller;

import com.carcare.common.BaseContext;
import com.carcare.common.Result;
import com.carcare.entity.CouponOrder;
import com.carcare.mapper.CouponOrderMapper;
import com.carcare.service.VoucherSeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀接口（车主侧）：需登录但不限角色，/api/seckill/** 走登录拦截器
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Tag(name = "09-优惠券秒杀", description = "抢券/余量查询/结果轮询")
public class SeckillController {

    private final VoucherSeckillService seckillService;
    private final CouponOrderMapper couponOrderMapper;

    @PostMapping("/coupons/{couponId}")
    @Operation(summary = "抢购优惠券", description = "Redis+Lua 预减库存后投递 MQ 异步落库，立即返回领券记录 id（排队中）")
    public Result<Long> seckill(@PathVariable Long couponId) {
        Long userId = BaseContext.getUserId();
        return Result.success(seckillService.seckill(couponId, userId));
    }

    @GetMapping("/orders/{couponOrderId}")
    @Operation(summary = "轮询领券结果", description = "异步链路：0=排队中 1=领取成功；前端拿到 id 后短轮询")
    public Result<Integer> seckillResult(@PathVariable Long couponOrderId) {
        CouponOrder order = couponOrderMapper.selectById(couponOrderId);
        return Result.success(order == null ? 0 : 1);
    }

    @GetMapping("/coupons/{couponId}/stock")
    @Operation(summary = "查询剩余库存", description = "Redis 预减视角的实时余量")
    public Result<Long> stock(@PathVariable Long couponId) {
        return Result.success(seckillService.remainingStock(couponId));
    }
}
