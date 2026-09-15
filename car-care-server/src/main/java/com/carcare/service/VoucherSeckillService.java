package com.carcare.service;

import com.carcare.common.BusinessException;
import com.carcare.config.RabbitConfig;
import com.carcare.dto.SeckillMessage;
import com.carcare.entity.Coupon;
import com.carcare.entity.CouponOrder;
import com.carcare.mapper.CouponMapper;
import com.carcare.mapper.CouponOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 优惠券秒杀服务（阶段2 核心）。
 * <p>
 * 整体链路：Redis 预减库存（Lua 原子判断）→ 抢中后落库（DB 条件扣减兜底）→ 失败补偿回滚。
 * <p>
 * 三层防超卖：
 * 1) Lua 脚本在 Redis 单线程内原子完成「查库存+查重复+扣减+记名」，并发请求天然串行；
 * 2) 领券记录表 (coupon_id,user_id,type) 唯一索引，重复落库直接被 DB 拒绝；
 * 3) 扣库存 UPDATE 带 stock > 0 条件，即使前两层被绕过也不可能扣成负数。
 * <p>
 * 领券 id 由号段发号器生成（SegmentIdService），趋势递增且批发取号，DB 压力 1/100。
 * <p>
 * 阶段2b 计划：抢中后不再同步落库，改为投递 RabbitMQ 异步下单 + 死信延迟关单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherSeckillService {

    private final StringRedisTemplate stringRedisTemplate;
    private final CouponMapper couponMapper;
    private final CouponOrderMapper couponOrderMapper;
    private final SegmentService segmentService;
    private final com.carcare.common.CacheHelper cacheHelper;
    private final RabbitTemplate rabbitTemplate;

    public static final String BIZ_COUPON_ORDER = "coupon_order";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill_voucher.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static String stockKey(Long couponId) {
        return "seckill:voucher:stock:" + couponId;
    }

    private static String successKey(Long couponId) {
        return "seckill:voucher:success:" + couponId;
    }

    /**
     * 秒杀主流程（阶段2b 异步版）：
     * Redis 预减成功 → 发号 → 投递 MQ → 立即返回领券记录 id（前端可轮询该 id 查结果）。
     * MQ 投递失败则同步补偿回滚 Redis，保证「用户资格」不被白扣。
     *
     * @return 预生成的领券记录 id（异步落库完成后即可查到）
     */
    public Long seckill(Long couponId, Long userId) {
        initStockIfNeeded(couponId);
        Long code = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Arrays.asList(stockKey(couponId), successKey(couponId)),
                String.valueOf(couponId), String.valueOf(userId));
        if (code == null) {
            throw new BusinessException("抢购失败，请重试");
        }
        switch (code.intValue()) {
            case 1 -> throw new BusinessException("优惠券已被抢完");
            case 2 -> throw new BusinessException("您已领取过该优惠券");
            default -> { /* 0 = 抢中 */ }
        }
        long couponOrderId = segmentService.nextId(BIZ_COUPON_ORDER);
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.SECKILL_EXCHANGE,
                    RabbitConfig.SECKILL_ROUTING_KEY,
                    new SeckillMessage(couponId, userId, couponOrderId));
        } catch (Exception e) {
            // 投递失败：回滚 Redis 预减，用户可重试
            stringRedisTemplate.opsForValue().increment(stockKey(couponId));
            stringRedisTemplate.opsForSet().remove(successKey(couponId), String.valueOf(userId));
            log.error("秒杀消息投递失败，已回滚 Redis couponId={} userId={}", couponId, userId, e);
            throw new BusinessException("抢购失败，请重试");
        }
        return couponOrderId;
    }

    /**
     * 消费端落库（SeckillConsumer 调用）。
     * 幂等三重保障：① Redis 已保证一人一次，重复消息概率极低；
     * ② 唯一索引 (coupon_id,user_id,type) 冲突 → 视为已处理，正常 ack；
     * ③ DB 条件扣减 stock>0 兜底，扣不到说明库存异常，记录告警。
     * 落库失败不回滚 Redis：Redis 是库存唯一事实源，
     * 消息重试直至成功（毒消息进死信人工处理），保证「预减=落库」最终一致
     */
    public void persist(SeckillMessage msg) {
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if (coupon == null) {
            throw new BusinessException("优惠券不存在，消息丢弃");
        }
        if (couponMapper.decreaseStock(msg.getCouponId()) == 0) {
            // DB 库存与 Redis 不一致（理论上仅人为改库会触发）
            log.warn("DB库存扣减为0，可能数据不一致 couponId={}", msg.getCouponId());
        }
        CouponOrder order = new CouponOrder();
        order.setId(msg.getCouponOrderId());
        order.setCouponId(msg.getCouponId());
        order.setUserId(msg.getUserId());
        order.setType(1); // 1=秒杀领取
        order.setStatus(1); // 1=未使用
        order.setStartTime(coupon.getValidStartTime());
        order.setEndTime(coupon.getValidEndTime());
        try {
            couponOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 = 重复消费，幂等吞掉；回补刚才多扣的 DB 库存
            couponMapper.increaseStock(msg.getCouponId());
            log.info("重复消息幂等丢弃 msg={}", msg);
        }
    }

    /**
     * 首次请求时把 DB 库存装载到 Redis。
     * 用 setIfAbsent 原子装载：并发下多个线程同时初始化也只有一次 SET 生效，
     * 失败方直接落到 Lua 判断（此时 key 必已存在），
     * 不需要互斥锁——早期锁版本里未抢到锁的线程会空手返回，
     * 导致 Lua 把「key 尚不存在」误判为售罄（压测实测丢过 7 个请求，已修复）
     */
    private void initStockIfNeeded(Long couponId) {
        String key = stockKey(couponId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getStock() <= 0) {
            throw new BusinessException("优惠券不存在或无库存");
        }
        if (coupon.getValidEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("优惠券活动已结束");
        }
        long ttl = Math.max(Duration.between(LocalDateTime.now(), coupon.getValidEndTime()).getSeconds(), 60);
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(coupon.getStock()),
                ttl, java.util.concurrent.TimeUnit.SECONDS);
        log.info("秒杀库存装载 couponId={} stock={}", couponId, coupon.getStock());
    }

    /** 剩余库存（Redis 视角，可能领先 DB 落库进度） */
    public Long remainingStock(Long couponId) {
        initStockIfNeeded(couponId);
        String v = stringRedisTemplate.opsForValue().get(stockKey(couponId));
        return v == null ? 0 : Long.parseLong(v);
    }

    /**
     * 管理端发布/重置秒杀：以 DB 库存为准重建 Redis 预减库存，并清空领取名单。
     * 用于压测前重置现场，或运营改库存后手动同步
     */
    public void publish(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        stringRedisTemplate.delete(List.of(stockKey(couponId), successKey(couponId)));
        long ttl = Math.max(Duration.between(LocalDateTime.now(), coupon.getValidEndTime()).getSeconds(), 60);
        stringRedisTemplate.opsForValue().set(stockKey(couponId), String.valueOf(coupon.getStock()), ttl, java.util.concurrent.TimeUnit.SECONDS);
        log.info("秒杀已发布 couponId={} stock={}", couponId, coupon.getStock());
    }
}
