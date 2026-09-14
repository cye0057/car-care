package com.carcare.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存载体：在值内部记录「逻辑过期时间」，物理 TTL 不设或设很长。
 * 判断是否可用看 expireAt 而非 Redis TTL——这是缓存击穿的逻辑过期方案：
 * 过期后不直接删 key，而是让抢到锁的线程异步重建，其余线程先返回旧值，
 * 高并发下不会出现「所有请求同时打到 DB」的击穿场景。
 * data 为 null 且 expireAt 有效时，代表「空值缓存」，用于拦截缓存穿透。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheData {
    private Object data;
    private long expireAt;
}
