package com.carcare.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.carcare.entity.Store;
import com.carcare.mapper.OrderMapper;
import com.carcare.mapper.ReviewMapper;
import com.carcare.mapper.StoreMapper;
import com.carcare.entity.Order;
import com.carcare.entity.Review;
import com.carcare.vo.StoreRankVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 附近门店（GEO）+ 热度排行榜（ZSet）服务（阶段3）。
 * <p>
 * GEO 原理：Redis GEO 底层是 ZSet，把经纬度通过 GeoHash 编码成 52 位整数作为 score，
 * 「按距离排序+范围过滤」= 对 score 区间做 ZRANGEBYSCORE 变体，因此天然支持海量点位检索。
 * 启动时全量装载营业门店坐标；写路径（新增/改坐标/删除）实时同步。
 * <p>
 * 热榜：热度分 = 近7天有效订单数 + 近7天评价数（业务可按 GMV/评分加权扩展）。
 * 由 HotStoreTask 定时聚合 DB 刷新 ZSet（member=门店id，score=热度），
 * 榜单查询即 ZREVRANGE——把「每次查询实时聚合」的 O(查询) 成本摊平成「每分钟一次」的固定成本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoStoreService implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final StoreMapper storeMapper;
    private final OrderMapper orderMapper;
    private final ReviewMapper reviewMapper;

    public static final String GEO_KEY = "cache:store:geo";
    public static final String HOT_KEY = "cache:store:hot:zset";

    /** 启动装载：全量重建 GEO（幂等，多实例部署时各实例装载结果一致） */
    @Override
    public void run(ApplicationArguments args) {
        try {
            loadGeoFromDb();
            refreshHot();
        } catch (Exception e) {
            log.warn("启动装载 GEO/热榜失败，等待定时任务兜底", e);
        }
    }

    /** 从 DB 全量重建 GEO 集合 */
    public void loadGeoFromDb() {
        stringRedisTemplate.delete(GEO_KEY);
        List<Store> stores = storeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Store>()
                        .eq(Store::getStatus, 1));
        for (Store s : stores) {
            geoAdd(s);
        }
        log.info("GEO 装载完成，门店数={}", stores.size());
    }

    /** 单店写入/更新 GEO（门店写路径调用，坐标变更即生效） */
    public void geoAdd(Store store) {
        stringRedisTemplate.opsForGeo().add(GEO_KEY,
                new Point(store.getLng().doubleValue(), store.getLat().doubleValue()),
                String.valueOf(store.getId()));
    }

    public void geoRemove(Long storeId) {
        stringRedisTemplate.opsForGeo().remove(GEO_KEY, String.valueOf(storeId));
    }

    /**
     * 附近门店：按距离升序返回 limit 家
     *
     * @param radiusKm 搜索半径（公里）
     */
    public List<StoreRankVO> nearby(double lng, double lat, double radiusKm, int limit) {
        Circle circle = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(GEO_KEY, circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .includeCoordinates()
                                .sortAscending()
                                .limit(limit));
        List<StoreRankVO> list = new ArrayList<>();
        if (results == null) {
            return list;
        }
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
            Long storeId = Long.valueOf(r.getContent().getName());
            Store store = storeMapper.selectById(storeId);
            if (store == null || store.getStatus() == 0) {
                continue; // 已休息/删除的门店不展示（GEO 同步滞后的兜底）
            }
            StoreRankVO vo = new StoreRankVO();
            BeanUtils.copyProperties(store, vo);
            // Spring Data 的 Distance 单位在不同版本随查询单位漂移，
            // 直接用 GEO 返回的坐标做 haversine 计算，结果确定且可单测
            Point p = r.getContent().getPoint();
            if (p != null) {
                vo.setDistanceKm(Math.round(haversineKm(lat, lng, p.getY(), p.getX()) * 100) / 100.0);
            } else {
                vo.setDistanceKm(Math.round(r.getDistance().getValue()) / 100.0);
            }
            list.add(vo);
        }
        return list;
    }

    /** 球面两点距离（km），haversine 公式 */
    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * 刷新热度榜：近7天有效订单（排除已取消）+ 近7天评价，按门店聚合后整体重建 ZSet。
     * 重建而非增量：单店热度是聚合值，全量覆盖最简单且无中间态
     */
    public void refreshHot() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Map<Long, Double> hotMap = new HashMap<>();
        // 订单聚合：GROUP BY store_id
        for (Map<String, Object> row : orderMapper.selectMaps(
                new QueryWrapper<Order>().select("store_id", "COUNT(*) AS cnt")
                        .ge("order_time", since).ne("status", 5).groupBy("store_id"))) {
            hotMap.merge(((Number) row.get("store_id")).longValue(),
                    ((Number) row.get("cnt")).doubleValue(), Double::sum);
        }
        // 评价聚合
        for (Map<String, Object> row : reviewMapper.selectMaps(
                new QueryWrapper<Review>().select("store_id", "COUNT(*) AS cnt")
                        .ge("create_time", since).groupBy("store_id"))) {
            hotMap.merge(((Number) row.get("store_id")).longValue(),
                    ((Number) row.get("cnt")).doubleValue(), Double::sum);
        }
        if (hotMap.isEmpty()) {
            stringRedisTemplate.delete(HOT_KEY);
            return;
        }
        // 先删后写，保证榜单无残留
        stringRedisTemplate.delete(HOT_KEY);
        hotMap.forEach((storeId, score) ->
                stringRedisTemplate.opsForZSet().add(HOT_KEY, String.valueOf(storeId), score));
        log.info("热度榜刷新完成，上榜门店数={}", hotMap.size());
    }

    /** 热榜查询：ZREVRANGE 取 topN，门店详情走阶段1缓存 */
    public List<StoreRankVO> hotStores(int limit) {
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(HOT_KEY, 0, limit - 1L);
        List<StoreRankVO> list = new ArrayList<>();
        if (tuples == null) {
            return list;
        }
        for (var t : tuples) {
            Store store = storeMapper.selectById(Long.valueOf(t.getValue()));
            if (store == null || store.getStatus() == 0) {
                continue;
            }
            StoreRankVO vo = new StoreRankVO();
            BeanUtils.copyProperties(store, vo);
            vo.setHotScore(t.getScore());
            list.add(vo);
        }
        return list;
    }
}
