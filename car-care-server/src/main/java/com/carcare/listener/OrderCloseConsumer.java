package com.carcare.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.config.RabbitConfig;
import com.carcare.entity.Order;
import com.carcare.mapper.OrderMapper;
import com.carcare.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 延迟关单消费者：消息在延迟队列 TTL 到期后经死信路由到这里。
 * 幂等靠「状态判断」：仅当订单仍是待支付才关闭；
 * 已支付/已取消的订单直接 ack 跳过——同一订单消息重复投递也安全。
 * 对比苍穹式定时轮询：无需扫表，关单精度由 TTL 控制，DB 零空转查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseConsumer {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @RabbitListener(queues = RabbitConfig.ORDER_CLOSE_QUEUE)
    public void onClose(Long orderId, Message raw, Channel channel) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("关单消息对应订单不存在 orderId={}", orderId);
            } else if (order.getStatus() == 1) {
                orderService.changeStatus(orderId, 5, "超时未支付，系统自动关闭");
                log.info("订单超时自动关闭 orderId={} orderNo={}", orderId, order.getOrderNo());
            } else {
                log.debug("订单状态 {} 无需关闭 orderId={}", order.getStatus(), orderId);
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("关单处理异常，nack 不重队 orderId={}", orderId, e);
            channel.basicNack(tag, false, false);
        }
    }
}
