package com.carcare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.Result;
import com.carcare.dto.PageQuery;
import com.carcare.entity.ServiceItem;
import com.carcare.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 保养项目管理接口：项目的增删改查与起售/停售状态切换
 */
@RestController
@RequestMapping("/api/admin/items")
@RequiredArgsConstructor
@Tag(name = "04-保养项目", description = "服务项目 CRUD 与上下架")
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    @Operation(summary = "项目分页查询", description = "支持 storeId/categoryId/name/status 过滤")
    public Result<Page<ServiceItem>> page(PageQuery query) {
        return Result.success(itemService.page(query));
    }

    @GetMapping("/by-store/{storeId}")
    @Operation(summary = "门店在售项目列表", description = "车主端下单数据源")
    public Result<List<ServiceItem>> listByStore(@PathVariable Long storeId) {
        return Result.success(itemService.listByStore(storeId));
    }

    @PostMapping
    @Operation(summary = "新增项目")
    public Result<Void> save(@RequestBody ServiceItem item) {
        itemService.save(item);
        return Result.success();
    }

    @PutMapping
    @Operation(summary = "修改项目")
    public Result<Void> update(@RequestBody ServiceItem item) {
        itemService.update(item);
        return Result.success();
    }

    @PutMapping("/{id}/status/{status}")
    @Operation(summary = "起售/停售", description = "status=1 起售，status=0 停售；被套餐引用时停售将被拒绝")
    public Result<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        itemService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除项目", description = "仅停售项目可删")
    public Result<Void> delete(@PathVariable Long id) {
        itemService.delete(id);
        return Result.success();
    }
}
