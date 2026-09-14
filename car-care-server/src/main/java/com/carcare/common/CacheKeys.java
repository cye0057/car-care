package com.carcare.common;

/**
 * 缓存 key 统一定义，避免各 Service 手写字符串导致前缀漂移
 */
public class CacheKeys {

    /** 门店详情（逻辑过期） */
    public static final String STORE_DETAIL = "cache:store:detail:";
    /** 营业门店列表（TTL+随机抖动） */
    public static final String STORE_LIST_ENABLED = "cache:store:list:enabled";
    /** 重建互斥锁 */
    public static final String STORE_LOCK = "lock:store:detail:";

    public static String storeDetail(Long id) {
        return STORE_DETAIL + id;
    }

    public static String storeLock(Long id) {
        return STORE_LOCK + id;
    }
}
