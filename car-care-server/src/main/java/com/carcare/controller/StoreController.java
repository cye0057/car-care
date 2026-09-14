package com.carcare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.Result;
import com.carcare.dto.PageQuery;
import com.carcare.entity.Store;
import com.carcare.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 门店管理接口（/api/admin 前缀，要求管理员角色）
 */
@RestController
@RequestMapping("/api/admin/stores")
@RequiredArgsConstructor
@Tag(name = "02-门店管理", description = "门店 CRUD 与查询")
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    @Operation(summary = "门店分页查询", description = "支持 name 模糊、status 过滤")
    public Result<Page<Store>> page(PageQuery query) {
        return Result.success(storeService.page(query));
    }

    @GetMapping("/enabled")
    @Operation(summary = "营业中门店列表", description = "供下拉框/选择器使用")
    public Result<List<Store>> listEnabled() {
        return Result.success(storeService.listEnabled());
    }

    @GetMapping("/{id}")
    @Operation(summary = "门店详情", description = "阶段1：逻辑过期+互斥锁+空值缓存的读路径")
    public Result<Store> detail(@PathVariable Long id) {
        return Result.success(storeService.getByIdCached(id));
    }

    @PostMapping
    @Operation(summary = "新增门店")
    public Result<Void> save(@RequestBody Store store) {
        storeService.save(store);
        return Result.success();
    }

    @PutMapping
    @Operation(summary = "修改门店")
    public Result<Void> update(@RequestBody Store store) {
        storeService.update(store);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除门店")
    public Result<Void> delete(@PathVariable Long id) {
        storeService.delete(id);
        return Result.success();
    }
}
