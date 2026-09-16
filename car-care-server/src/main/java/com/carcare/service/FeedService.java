package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.entity.Review;
import com.carcare.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feed 流查询服务（阶段4 核心）：推模式收件箱 + 拉模式大账号 合并，双游标滚动分页。
 * <p>
 * 游标二元组（前端每页原样回传）：
 * - maxId：上一页已见最小笔记 id → 拉模式取 id < maxId；同时用于过滤收件箱
 *   边界重复项（score == beginTime 的已见条目 id >= maxId，直接丢弃）；
 * - beginTime：上一页最后一条的发布时间戳 → 收件箱取 score <= beginTime。
 * 翻页期间新帖推入收件箱头部时，score 边界天然隔离（新帖 score 更大不落入本区间），
 * 不需要 offset 跳页——offset 与 score 边界叠加会双重跳过数据（初版实测踩坑，已修正）。
 * <p>
 * 为什么不用 limit(pageNum, size) 深分页：offset 越大 MySQL/Redis 扫描越多，
 * 信息流无限下拉场景游标分页每页成本恒定。
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final int PAGE_SIZE = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewMapper reviewMapper;
    private final FollowService followService;
    private final ReviewService reviewService;

    public Map<String, Object> queryFeed(Long userId, Long maxId, Long beginTime) {
        long curMaxId = maxId == null ? Long.MAX_VALUE : maxId;
        long curBeginTime = beginTime == null ? Long.MAX_VALUE : beginTime;

        // 1) 拉模式：关注的「大账号」发布的更早笔记（id 降序游标）
        List<Review> pulled = new ArrayList<>();
        List<Long> followees = followService.followingIds(userId).stream()
                .filter(followService::isBigAccount).toList();
        if (!followees.isEmpty()) {
            pulled = reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                    .in(Review::getUserId, followees)
                    .lt(Review::getId, curMaxId)
                    .orderByDesc(Review::getId)
                    .last("LIMIT " + PAGE_SIZE));
        }

        // 2) 推模式：收件箱 score <= beginTime，多取一条供边界去重
        Set<ZSetOperations.TypedTuple<String>> inboxTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(ReviewService.FEED_INBOX_KEY + userId,
                        0, (double) curBeginTime, 0, PAGE_SIZE + 1L);
        List<ZSetOperations.TypedTuple<String>> inbox = new ArrayList<>();
        List<Review> pushed = new ArrayList<>();
        if (inboxTuples != null) {
            List<Long> ids = inboxTuples.stream().map(ZSetOperations.TypedTuple::getValue)
                    .map(Long::valueOf).toList();
            Map<Long, Review> byId = ids.isEmpty() ? Map.of()
                    : reviewMapper.selectByIds(ids).stream()
                    .collect(HashMap::new, (m, r) -> m.put(r.getId(), r), HashMap::putAll);
            for (ZSetOperations.TypedTuple<String> t : inboxTuples) {
                Review r = byId.get(Long.valueOf(t.getValue()));
                if (r == null || r.getId() >= curMaxId) {
                    continue; // 上一页边界已见项
                }
                if (pushed.size() >= PAGE_SIZE) {
                    break;
                }
                inbox.add(t);
                pushed.add(r);
            }
        }

        // 3) 合并按 id 降序并去重：同一笔记可能既在收件箱（历史推送）又被拉取
        //    （作者转为大账号/阈值调整），按 id 保留第一条（时间序一致）
        List<Review> merged = new ArrayList<>(pulled);
        merged.addAll(pushed);
        merged.sort(Comparator.comparing(Review::getId, Comparator.reverseOrder()));
        Set<Long> seen = new java.util.HashSet<>();
        merged = merged.stream().filter(r -> seen.add(r.getId())).toList();
        if (merged.size() > PAGE_SIZE) {
            merged = merged.subList(0, PAGE_SIZE);
        }

        // 4) 下一页游标：min(id) 与 min(score)
        long nextMaxId = merged.stream().mapToLong(Review::getId).min().orElse(curMaxId);
        long nextBeginTime = inbox.isEmpty() ? curBeginTime
                : Math.min(curBeginTime, inbox.get(inbox.size() - 1).getScore().longValue());

        Map<String, Object> result = new HashMap<>();
        result.put("list", reviewService.toVOList(merged, userId));
        result.put("nextMaxId", nextMaxId);
        result.put("nextBeginTime", nextBeginTime);
        result.put("hasMore", pulled.size() == PAGE_SIZE || pushed.size() == PAGE_SIZE);
        return result;
    }

    /** 个人主页：我发布的笔记（逻辑删除自动过滤） */
    public List<Review> myReviews(Long userId) {
        return reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                .eq(Review::getUserId, userId).orderByDesc(Review::getId));
    }
}
