package com.carcare.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.common.Result;
import com.carcare.entity.Order;
import com.carcare.entity.ServiceItem;
import com.carcare.entity.Store;
import com.carcare.mapper.OrderMapper;
import com.carcare.mapper.ServiceItemMapper;
import com.carcare.mapper.StoreMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作台统计接口：管理台首页概览卡片的聚合数据
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "08-工作台", description = "首页统计概览")
public class DashboardController {

    private final StoreMapper storeMapper;
    private final ServiceItemMapper itemMapper;
    private final OrderMapper orderMapper;

    @GetMapping("/stats")
    @Operation(summary = "统计概览", description = "门店数/项目数/订单数/今日订单/待支付/施工中")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("storeCount", storeMapper.selectCount(Wrappers.<Store>lambdaQuery()));
        stats.put("itemCount", itemMapper.selectCount(Wrappers.<ServiceItem>lambdaQuery()));
        stats.put("orderCount", orderMapper.selectCount(Wrappers.<Order>lambdaQuery()));
        stats.put("todayOrderCount", orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .ge(Order::getOrderTime, LocalDate.now().atStartOfDay())));
        stats.put("pendingCount", orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, 1)));
        stats.put("workingCount", orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, 3)));
        return Result.success(stats);
    }
}
