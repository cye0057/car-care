package com.carcare.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.common.Result;
import com.carcare.entity.PackageItem;
import com.carcare.entity.ServiceItem;
import com.carcare.entity.ServicePackage;
import com.carcare.entity.Store;
import com.carcare.mapper.PackageItemMapper;
import com.carcare.mapper.ServiceItemMapper;
import com.carcare.mapper.ServicePackageMapper;
import com.carcare.service.ReviewService;
import com.carcare.service.StoreService;
import com.carcare.vo.BlogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 车主侧门店浏览接口：只读、仅暴露营业中/在售数据。
 * 与 /api/admin 管理接口的区别：按角色分域鉴权（拦截器对 /api/admin 要求 role=0），
 * 详情复用阶段1的缓存读路径，车主端高并发浏览不打 DB
 */
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
@Tag(name = "15-车主端浏览", description = "门店/项目/套餐/评价")
public class StoreUserController {

    private final StoreService storeService;
    private final ServiceItemMapper itemMapper;
    private final ServicePackageMapper packageMapper;
    private final PackageItemMapper packageItemMapper;
    private final ReviewService reviewService;

    @GetMapping("/nearby")
    @Operation(summary = "附近/全部营业门店", description = "复用门店列表缓存")
    public Result<List<Store>> list() {
        return Result.success(storeService.listEnabled());
    }

    @GetMapping("/{id}")
    @Operation(summary = "门店详情", description = "逻辑过期缓存读路径（阶段1）")
    public Result<Store> detail(@PathVariable Long id) {
        return Result.success(storeService.getByIdCached(id));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "门店在售项目")
    public Result<List<ServiceItem>> items(@PathVariable Long id) {
        return Result.success(itemMapper.selectList(Wrappers.<ServiceItem>lambdaQuery()
                .eq(ServiceItem::getStoreId, id).eq(ServiceItem::getStatus, 1)));
    }

    @GetMapping("/{id}/packages")
    @Operation(summary = "门店启用套餐（含明细快照）")
    public Result<List<Map<String, Object>>> packages(@PathVariable Long id) {
        List<ServicePackage> packages = packageMapper.selectList(Wrappers.<ServicePackage>lambdaQuery()
                .eq(ServicePackage::getStoreId, id).eq(ServicePackage::getStatus, 1));
        return Result.success(packages.stream().map(p -> Map.<String, Object>of(
                "pkg", p,
                "items", packageItemMapper.selectList(Wrappers.<PackageItem>lambdaQuery()
                        .eq(PackageItem::getPackageId, p.getId())))).toList());
    }

    @GetMapping("/{id}/reviews")
    @Operation(summary = "门店评价分页")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<BlogVO>> reviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(reviewService.pageByStore(id, pageNum, pageSize));
    }
}
