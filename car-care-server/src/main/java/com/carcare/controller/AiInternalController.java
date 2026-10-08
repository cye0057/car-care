package com.carcare.controller;

import com.carcare.common.Result;
import com.carcare.entity.Coupon;
import com.carcare.entity.ServiceItem;
import com.carcare.entity.Store;
import com.carcare.service.AiInternalService;
import com.carcare.vo.AiPackageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 服务的内部只读接口。
 * <p>
 * 调用方只有 car-care-ai（Python），鉴权走 {@code X-Internal-Token} 而不是车主 JWT，
 * 见 InternalTokenInterceptor。这些接口一律只读，且只返回「营业中/在售/启用」的数据，
 * 与车主端浏览口径完全一致。
 * <p>
 * 注意：这里刻意**不提供**任何按 userId 查询的接口。用户的私有数据（订单、车辆、已领券）
 * 由 Java 在收到对话请求时按 JWT 身份查好后随请求下发，AI 服务没有反查他人数据的入口。
 */
@RestController
@RequestMapping("/api/internal/ai")
@RequiredArgsConstructor
@Tag(name = "97-AI内部接口", description = "仅供 car-care-ai 服务调用，需内部令牌")
public class AiInternalController {

    private final AiInternalService aiInternalService;

    @GetMapping("/stores")
    @Operation(summary = "营业中门店", description = "复用门店列表缓存；city/keyword 模糊匹配")
    public Result<List<Store>> stores(@RequestParam(required = false) String city,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(aiInternalService.stores(city, keyword, clamp(limit)));
    }

    @GetMapping("/items")
    @Operation(summary = "在售服务项目", description = "只返回 status=1 的项目")
    public Result<List<ServiceItem>> items(@RequestParam(required = false) Long storeId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(aiInternalService.items(storeId, keyword, clamp(limit)));
    }

    @GetMapping("/packages")
    @Operation(summary = "启用中的套餐", description = "附拍平后的项目名，便于模型理解套餐内容")
    public Result<List<AiPackageVO>> packages(@RequestParam Long storeId,
                                             @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(aiInternalService.packages(storeId, clamp(limit)));
    }

    @GetMapping("/coupons")
    @Operation(summary = "可抢优惠券", description = "有效期内且有库存的平台券池，不含用户领取记录")
    public Result<List<Coupon>> coupons(@RequestParam(required = false) Long storeId,
                                       @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(aiInternalService.claimableCoupons(storeId, clamp(limit)));
    }

    /** 上限兜底：防止调用方传个 limit=100000 把整库拉走 */
    private int clamp(Integer limit) {
        if (limit == null || limit < 1) {
            return 5;
        }
        return Math.min(limit, 50);
    }
}
