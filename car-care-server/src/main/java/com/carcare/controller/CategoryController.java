package com.carcare.controller;

import com.carcare.common.Result;
import com.carcare.entity.ServiceCategory;
import com.carcare.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务分类管理接口：分类是服务项的归属维度，数据量小，全量返回不分页
 */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@Tag(name = "03-服务分类", description = "保养服务分类维护")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "分类列表", description = "按 sort 升序全量返回")
    public Result<List<ServiceCategory>> list() {
        return Result.success(categoryService.list());
    }

    @PostMapping
    @Operation(summary = "新增分类", description = "名称重复将被拒绝")
    public Result<Void> save(@RequestBody ServiceCategory category) {
        categoryService.save(category);
        return Result.success();
    }

    @PutMapping
    @Operation(summary = "修改分类")
    public Result<Void> update(@RequestBody ServiceCategory category) {
        categoryService.update(category);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类", description = "分类下仍有服务项时拒绝删除")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
