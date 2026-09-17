package com.carcare.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（阶段2b）。
 * <p>
 * 链路一：秒杀异步落库（削峰）
 *   生产者 → seckill.exchange → seckill.order.queue → 消费者写 DB
 *   用户请求在 Redis 预减成功后即可返回，DB 写入压力被队列摊平。
 * <p>
 * 链路二：延迟关单（死信队列）
 *   下单时投递 orderId 到 order.delay.queue（无消费者，TTL 到期）
 *   → 消息过期进入死信 → order.close.exchange → order.close.queue → 关单消费者
 *   替代「定时任务每几分钟扫全表」方案：零空转扫描，关单精度=消息投递时刻+TTL。
 * <p>
 * 注意：TTL 用队列级而非消息级——消息级 TTL 队首未过期会阻塞检查（队头阻塞），
 * 单一超时时长场景下队列级 TTL 没有乱序问题。
 * 消息体只携带 id，消费端回查 DB，避免大对象序列化与数据陈旧问题。
 */
@Configuration
public class RabbitConfig {

    /* ---------- 秒杀链路 ---------- */
    public static final String SECKILL_EXCHANGE = "carcare.seckill.exchange";
    public static final String SECKILL_QUEUE = "carcare.seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill";

    /* ---------- 延迟关单链路 ---------- */
    public static final String ORDER_DELAY_QUEUE = "carcare.order.delay.queue";
    public static final String ORDER_CLOSE_EXCHANGE = "carcare.order.close.exchange";
    public static final String ORDER_CLOSE_QUEUE = "carcare.order.close.queue";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    /** 未支付关单超时：默认 30 分钟；演示死信关单时可临时调小 */
    @Value("${carcare.order.close-ttl-ms:1800000}")
    private int orderCloseTtlMs;

    @Bean
    public MessageConverter jsonMessageConverter() {
        // JSON 消息体：管理台 15672 可直接肉眼查看，便于演示与排障
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    /** 延迟队列：没有消费者，消息 TTL 到期后经死信交换机转发 */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .deadLetterExchange(ORDER_CLOSE_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CLOSE_ROUTING_KEY)
                .ttl(orderCloseTtlMs)
                .build();
    }

    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(ORDER_CLOSE_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(ORDER_CLOSE_QUEUE).build();
    }

    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue()).to(orderCloseExchange()).with(ORDER_CLOSE_ROUTING_KEY);
    }
}
