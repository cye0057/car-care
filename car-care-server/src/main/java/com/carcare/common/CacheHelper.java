package com.carcare.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 缓存辅助组件：统一提供三样能力——
 * 1) 互斥锁：SETNX+过期，防止缓存失效瞬间并发重建（锁必须带超时，防死锁）
 * 2) 安全解锁：Lua 比对持有者再删除，防止误删别人的锁
 * 3) 延迟双删：更新 DB 删缓存后，延迟再删一次，清掉「删除窗口期」内
 *    被并发读请求写回缓存的旧值（Cache Aside 的补漏手段）
 */
@Component
@RequiredArgsConstructor
public class CacheHelper {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 解锁脚本：值等于本次持有的 token 才删除 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** 延迟双删调度线程池（守护线程，不阻塞 JVM 退出） */
    private static final ScheduledExecutorService DELAY_EXECUTOR =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "cache-double-delete");
                t.setDaemon(true);
                return t;
            });

    /**
     * 尝试获取互斥锁
     *
     * @return 持有者凭证 token；null 表示未抢到
     */
    public String tryLock(String lockKey, long expireSeconds) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(String lockKey, String token) {
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
    }

    /** 立即删除 + 延迟 delayMs 再删一次（双删的第二次） */
    public void deleteWithDoubleDelete(String... keys) {
        for (String key : keys) {
            redisTemplate.delete(key);
        }
        DELAY_EXECUTOR.schedule(() -> {
            for (String key : keys) {
                redisTemplate.delete(key);
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void set(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    /** 逻辑过期场景：物理 TTL 给很长（7天兜底），真实有效期由 CacheData.expireAt 控制 */
    public void setWithLogicalExpire(String key, CacheData data) {
        redisTemplate.opsForValue().set(key, data, 7, TimeUnit.DAYS);
    }
}
