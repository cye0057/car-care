package com.carcare.controller;

import com.carcare.common.BaseContext;
import com.carcare.common.Result;
import com.carcare.config.RabbitConfig;
import com.carcare.dto.OrderCreateDTO;
import com.carcare.entity.Order;
import com.carcare.service.OrderService;
import com.carcare.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户端订单接口（车主侧）：下单/支付/详情。
 * 下单成功后向延迟队列投递 orderId，TTL 到期自动关单——
 * 发送放在事务方法之外，保证消费者可见时订单必已提交
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "10-用户订单", description = "下单/支付/查询")
public class OrderUserController {

    private final OrderService orderService;
    private final PayService payService;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping
    @Operation(summary = "创建订单", description = "待支付状态；同时投递延迟关单消息（TTL 后未支付自动关闭）")
    public Result<Map<String, Object>> create(@RequestBody @Valid OrderCreateDTO dto) {
        Order order = orderService.createOrder(dto);
        // 默认交换机（""）按路由键=队列名直投延迟队列
        rabbitTemplate.convertAndSend("", RabbitConfig.ORDER_DELAY_QUEUE, order.getId());
        return Result.success(Map.of("orderId", order.getId(), "orderNo", order.getOrderNo()));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "发起支付", description = "校验订单归属与待支付状态，返回支付宝沙箱收银台跳转 URL；支付结果由异步回调驱动")
    public Result<String> pay(@PathVariable Long id) {
        return Result.success(payService.createPayUrl(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "取消订单", description = "仅本人待支付订单可取消（1→5）")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        orderService.cancelOwn(id, reason == null ? "车主主动取消" : reason);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情", description = "返回 {order, details, workOrder}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id));
    }

    @GetMapping("/my")
    @Operation(summary = "我的订单列表")
    public Result<Object> my(@RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = BaseContext.getUserId();
        return Result.success(orderService.pageByUser(userId, pageNum, pageSize));
    }
}
