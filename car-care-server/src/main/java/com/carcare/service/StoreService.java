package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.CacheData;
import com.carcare.common.CacheHelper;
import com.carcare.common.CacheKeys;
import com.carcare.dto.PageQuery;
import com.carcare.entity.Store;
import com.carcare.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 门店服务（阶段1：接入 Redis 缓存体系）。
 * <p>
 * 三类缓存问题的对应策略：
 * 1) 缓存穿透（查不存在的数据）：空值缓存——DB 查不到也写入 CacheData(null) 短 TTL，
 *    恶意 id 重复查询直接被缓存拦截，不再打到 MySQL；
 * 2) 缓存击穿（热点 key 到期瞬间并发回源）：逻辑过期——缓存不设物理 TTL（兜底7天），
 *    由 CacheData.expireAt 标记逻辑失效；失效后仅抢到互斥锁的线程重建缓存，
 *    其余线程直接返回旧值，读写均不阻塞；
 * 3) 缓存雪崩（大量 key 同一时刻到期）：TTL 叠加随机抖动（ttl + rand(jitter)），
 *    把失效时间点打散。
 * <p>
 * 一致性：Cache Aside（先写库再删缓存）+ 延迟双删，兜住并发读写导致的脏缓存；
 * cache.enabled 开关用于 JMeter 压测时对比「直查 DB」与「命中缓存」的性能差异。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreMapper storeMapper;
    private final CacheHelper cacheHelper;
    private final GeoStoreService geoStoreService;

    /** 缓存总开关（压测基线对比用） */
    @Value("${carcare.cache.enabled:true}")
    private boolean cacheEnabled;
    /** 基础 TTL 秒 */
    @Value("${carcare.cache.ttl-seconds:60}")
    private long ttlSeconds;
    /** 随机抖动上限秒（防雪崩） */
    @Value("${carcare.cache.jitter-seconds:30}")
    private long jitterSeconds;
    /** 空值缓存 TTL 秒（防穿透） */
    @Value("${carcare.cache.null-ttl-seconds:120}")
    private long nullTtlSeconds;

    private static final long LOCK_EXPIRE_SECONDS = 10;

    /** 分页查询走 DB：后台管理页数据要求实时准确，不做缓存 */
    public Page<Store> page(PageQuery query) {
        return storeMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<Store>lambdaQuery()
                        .like(StringUtils.hasText(query.getName()), Store::getName, query.getName())
                        .eq(query.getStatus() != null, Store::getStatus, query.getStatus())
                        .orderByAsc(Store::getId));
    }

    /**
     * 营业中门店列表：整表缓存 + TTL 随机抖动（雪崩防护）。
     * 缓存未命中时用互斥锁重建（击穿防护），未抢到锁的等待 50ms 重试一次
     */
    @SuppressWarnings("unchecked")
    public List<Store> listEnabled() {
        if (!cacheEnabled) {
            return queryEnabledFromDb();
        }
        Object cached = cacheHelper.get(CacheKeys.STORE_LIST_ENABLED);
        if (cached instanceof List<?> list && !list.isEmpty()) {
            return (List<Store>) list;
        }
        String token = cacheHelper.tryLock("lock:store:list", LOCK_EXPIRE_SECONDS);
        if (token == null) {
            sleepQuietly(50);
            Object retry = cacheHelper.get(CacheKeys.STORE_LIST_ENABLED);
            if (retry instanceof List<?> list && !list.isEmpty()) {
                return (List<Store>) list;
            }
            return queryEnabledFromDb();
        }
        try {
            List<Store> stores = queryEnabledFromDb();
            long ttl = ttlSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
            cacheHelper.set(CacheKeys.STORE_LIST_ENABLED, stores, ttl);
            return stores;
        } finally {
            cacheHelper.unlock("lock:store:list", token);
        }
    }

    /**
     * 门店详情：逻辑过期 + 互斥锁重建 + 空值缓存（击穿/穿透双防护）。
     * 逻辑过期意味着「过期后仍返回旧值」，牺牲极短时间的一致性换取零阻塞，
     * 适合首页热点商户这类读多写少场景
     */
    public Store getByIdCached(Long id) {
        if (!cacheEnabled) {
            return storeMapper.selectById(id);
        }
        String key = CacheKeys.storeDetail(id);
        Object cached = cacheHelper.get(key);
        if (cached instanceof CacheData cd) {
            boolean logicalExpired = System.currentTimeMillis() > cd.getExpireAt();
            if (!logicalExpired) {
                return (Store) cd.getData(); // 含空值缓存命中：data==null 直接返回 null，穿透被拦截
            }
            // 已逻辑过期：抢到锁的线程重建，其余线程返回旧值
            String token = cacheHelper.tryLock(CacheKeys.storeLock(id), LOCK_EXPIRE_SECONDS);
            if (token == null) {
                return (Store) cd.getData();
            }
            try {
                return rebuildDetailCache(id, key);
            } finally {
                cacheHelper.unlock(CacheKeys.storeLock(id), token);
            }
        }
        // 真正未命中：互斥锁重建，未抢到锁短暂等待后重试一次
        String token = cacheHelper.tryLock(CacheKeys.storeLock(id), LOCK_EXPIRE_SECONDS);
        if (token == null) {
            sleepQuietly(50);
            Object retry = cacheHelper.get(key);
            if (retry instanceof CacheData cd) {
                return (Store) cd.getData();
            }
            return storeMapper.selectById(id);
        }
        try {
            return rebuildDetailCache(id, key);
        } finally {
            cacheHelper.unlock(CacheKeys.storeLock(id), token);
        }
    }

    /** 回源 DB 并写回缓存；查不到写空值占位（短 TTL），防穿透 */
    private Store rebuildDetailCache(Long id, String key) {
        Store store = storeMapper.selectById(id);
        CacheData data;
        if (store == null) {
            data = new CacheData(null, System.currentTimeMillis() + nullTtlSeconds * 1000);
            log.debug("门店 {} 不存在，写入空值缓存 {}s", id, nullTtlSeconds);
        } else {
            long ttl = ttlSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
            data = new CacheData(store, System.currentTimeMillis() + ttl * 1000);
        }
        cacheHelper.setWithLogicalExpire(key, data);
        return store;
    }

    private List<Store> queryEnabledFromDb() {
        return storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                .eq(Store::getStatus, 1).orderByAsc(Store::getId));
    }

    /** 新增门店：状态缺省为营业；同步写入 GEO（阶段3） */
    public void save(Store store) {
        if (store.getStatus() == null) {
            store.setStatus(1);
        }
        storeMapper.insert(store);
        cacheHelper.deleteWithDoubleDelete(CacheKeys.STORE_LIST_ENABLED);
        if (store.getStatus() == 1) {
            geoStoreService.geoAdd(store);
        }
    }

    /**
     * 修改门店：Cache Aside 先写库，再立即删缓存 + 500ms 延迟双删。
     * GEO 同步：改后回查完整记录——营业中则更新坐标（可能改的就是经纬度），
     * 转为休息则从 GEO 摘除，避免「附近门店」推出不可约的店
     */
    public void update(Store store) {
        storeMapper.updateById(store);
        cacheHelper.deleteWithDoubleDelete(CacheKeys.storeDetail(store.getId()), CacheKeys.STORE_LIST_ENABLED);
        Store latest = storeMapper.selectById(store.getId());
        if (latest != null && latest.getStatus() == 1) {
            geoStoreService.geoAdd(latest);
        } else {
            geoStoreService.geoRemove(store.getId());
        }
    }

    /** 删除门店：缓存双删 + GEO 摘除（进行中订单校验阶段6补充） */
    public void delete(Long id) {
        storeMapper.deleteById(id);
        cacheHelper.deleteWithDoubleDelete(CacheKeys.storeDetail(id), CacheKeys.STORE_LIST_ENABLED);
        geoStoreService.geoRemove(id);
    }

    /** 保留原始 DB 查询给内部逻辑（如其他 Service 校验用） */
    public Store getById(Long id) {
        return storeMapper.selectById(id);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
