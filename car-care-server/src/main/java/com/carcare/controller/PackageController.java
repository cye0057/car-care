package com.carcare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.Result;
import com.carcare.dto.PageQuery;
import com.carcare.dto.PackageDTO;
import com.carcare.entity.ServicePackage;
import com.carcare.service.PackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 保养套餐管理接口：套餐与明细一体化维护，启用/禁用受项目在售状态约束
 */
@RestController
@RequestMapping("/api/admin/packages")
@RequiredArgsConstructor
@Tag(name = "05-套餐管理", description = "保养套餐 CRUD 与上下架")
public class PackageController {

    private final PackageService packageService;

    @GetMapping
    @Operation(summary = "套餐分页查询")
    public Result<Page<ServicePackage>> page(PageQuery query) {
        return Result.success(packageService.page(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "套餐详情", description = "返回 {package, items} 两部分，供编辑回显")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(packageService.detail(id));
    }

    @PostMapping
    @Operation(summary = "新增套餐", description = "itemIds 至少一项且全部在售，服务端生成价格快照")
    public Result<Void> save(@RequestBody PackageDTO dto) {
        packageService.save(dto);
        return Result.success();
    }

    @PutMapping
    @Operation(summary = "修改套餐", description = "传入 itemIds 时整组重建明细")
    public Result<Void> update(@RequestBody PackageDTO dto) {
        packageService.update(dto);
        return Result.success();
    }

    @PutMapping("/{id}/status/{status}")
    @Operation(summary = "启用/禁用", description = "空套餐不可启用")
    public Result<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        packageService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除套餐", description = "仅禁用的套餐可删")
    public Result<Void> delete(@PathVariable Long id) {
        packageService.delete(id);
        return Result.success();
    }
}
