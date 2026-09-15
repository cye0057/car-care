package com.carcare.listener;

import com.carcare.common.BusinessException;
import com.carcare.config.RabbitConfig;
import com.carcare.dto.SeckillMessage;
import com.carcare.service.VoucherSeckillService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀异步落库消费者。
 * 手动 ack（配置 acknowledge-mode: manual）：
 * - 成功/幂等冲突 → ack；
 * - 业务性失败（券不存在）→ ack 丢弃并告警（重试也不会成功）；
 * - 系统异常（DB 抖动等）→ nack 不重回原队列，防止毒消息死循环；
 *   生产环境应为该队列配 DLX 收集毒消息人工排查（阶段6 完善）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillConsumer {

    private final VoucherSeckillService seckillService;

    @RabbitListener(queues = RabbitConfig.SECKILL_QUEUE)
    public void onSeckill(SeckillMessage msg, Message raw, Channel channel) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();
        try {
            seckillService.persist(msg);
            channel.basicAck(tag, false);
        } catch (BusinessException e) {
            log.warn("秒杀消息业务性丢弃 msg={} reason={}", msg, e.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("秒杀消息处理异常，nack 不重队 msg={}", msg, e);
            channel.basicNack(tag, false, false);
        }
    }
}
