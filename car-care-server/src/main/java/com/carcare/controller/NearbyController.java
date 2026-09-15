package com.carcare.controller;

import com.carcare.common.Result;
import com.carcare.service.GeoStoreService;
import com.carcare.vo.StoreRankVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 附近门店/热榜接口（车主侧）：
 * 附近查询依赖 Redis GEO（启动装载+写路径实时同步），
 * 热榜依赖定时任务刷新的 ZSet
 */
@RestController
@RequestMapping("/api/nearby")
@RequiredArgsConstructor
@Tag(name = "11-附近与热榜", description = "GEO 附近门店 / ZSet 热度榜")
public class NearbyController {

    private final GeoStoreService geoStoreService;

    @GetMapping("/stores")
    @Operation(summary = "附近门店", description = "按距离升序返回半径内门店；默认坐标西湖文化广场(120.16,30.27)，半径5km")
    public Result<List<StoreRankVO>> nearby(@RequestParam(defaultValue = "120.16") Double lng,
                                            @RequestParam(defaultValue = "30.27") Double lat,
                                            @RequestParam(defaultValue = "5") Double radiusKm,
                                            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(geoStoreService.nearby(lng, lat, radiusKm, limit));
    }

    @GetMapping("/hot")
    @Operation(summary = "热度排行榜", description = "近7天有效订单+评价聚合，每分钟刷新")
    public Result<List<StoreRankVO>> hot(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(geoStoreService.hotStores(limit));
    }
}
