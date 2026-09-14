package com.carcare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.Result;
import com.carcare.dto.PageQuery;
import com.carcare.service.OrderService;
import com.carcare.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单管理接口：列表/详情为只读查询，状态流转走状态机校验，
 * 工单生成与收单由 Service 在流转时自动联动
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "07-订单管理", description = "订单查询与状态流转")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "订单分页查询", description = "name 模糊匹配订单号；返回数据已回填门店/车主/车牌名称")
    public Result<Page<OrderVO>> page(PageQuery query) {
        return Result.success(orderService.page(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情", description = "返回 {order, details, workOrder}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id));
    }

    @PutMapping("/{id}/status/{status}")
    @Operation(summary = "订单状态流转", description = "仅允许：1→2/5、2→3、3→4、4→6；取消需携带 cancelReason")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @PathVariable Integer status,
                                     @RequestParam(required = false) String cancelReason) {
        orderService.changeStatus(id, status, cancelReason);
        return Result.success();
    }
}
