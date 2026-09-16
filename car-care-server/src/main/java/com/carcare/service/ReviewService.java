package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BusinessException;
import com.carcare.entity.Review;
import com.carcare.entity.Store;
import com.carcare.entity.User;
import com.carcare.mapper.ReviewMapper;
import com.carcare.mapper.StoreMapper;
import com.carcare.mapper.UserMapper;
import com.carcare.vo.BlogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 养车笔记/门店评价服务（阶段4）。
 * 发帖采用「写扩散」推模式：作者非大账号时，把笔记 id 以时间戳为 score
 * 推进每个粉丝的收件箱 ZSet（feed:inbox:{userId}），粉丝刷 Feed 只读自己的收件箱——
 * 读时零聚合，代价在写时；大账号（粉丝超阈值）不推，避免一次发帖打爆 Redis。
 * 点赞用 Redis Set 存明细（天然去重），DB liked_count 做计数快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final StoreMapper storeMapper;
    private final FollowService followService;
    private final StringRedisTemplate stringRedisTemplate;

    public static final String FEED_INBOX_KEY = "feed:inbox:";
    public static final String LIKED_KEY_PREFIX = "like:blog:";

    /** 发布笔记：落库 + 推模式写扩散到粉丝收件箱 */
    public Long publish(Long userId, Review review) {
        if (review.getStoreId() == null) {
            throw new BusinessException("必须关联门店");
        }
        Store store = storeMapper.selectById(review.getStoreId());
        if (store == null) {
            throw new BusinessException("门店不存在");
        }
        review.setId(null);
        review.setUserId(userId);
        review.setLikedCount(0);
        reviewMapper.insert(review);
        pushToFollowers(review);
        return review.getId();
    }

    /** 写扩散：非大账号才推；粉丝收件箱 ZSet member=笔记id score=发布时间毫秒 */
    private void pushToFollowers(Review review) {
        if (followService.isBigAccount(review.getUserId())) {
            log.debug("大账号发帖走拉模式，不写扩散 userId={}", review.getUserId());
            return;
        }
        List<Long> fans = followService.followerIds(review.getUserId());
        if (fans.isEmpty()) {
            return;
        }
        long score = review.getCreateTime() == null ? System.currentTimeMillis()
                : review.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (Long fanId : fans) {
            stringRedisTemplate.opsForZSet().add(FEED_INBOX_KEY + fanId,
                    String.valueOf(review.getId()), score);
        }
        log.info("笔记 {} 已推送到 {} 个粉丝收件箱", review.getId(), fans.size());
    }

    /** 点赞：Set 去重，首次成功才 +1 计数 */
    public boolean like(Long reviewId, Long userId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("笔记不存在");
        }
        Long added = stringRedisTemplate.opsForSet()
                .add(LIKED_KEY_PREFIX + reviewId, String.valueOf(userId));
        if (added != null && added > 0) {
            reviewMapper.incrLikedCount(reviewId, 1);
            return true;
        }
        return false; // 重复点赞幂等
    }

    /** 取消点赞 */
    public boolean unlike(Long reviewId, Long userId) {
        Long removed = stringRedisTemplate.opsForSet()
                .remove(LIKED_KEY_PREFIX + reviewId, String.valueOf(userId));
        if (removed != null && removed > 0) {
            reviewMapper.incrLikedCount(reviewId, -1);
            return true;
        }
        return false;
    }

    /** 门店评价分页（DB 直查，实时性优先，量小不走缓存） */
    public Page<BlogVO> pageByStore(Long storeId, Integer pageNum, Integer pageSize) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Review>lambdaQuery().eq(Review::getStoreId, storeId)
                        .orderByDesc(Review::getId));
        Page<BlogVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(toVOList(page.getRecords(), null));
        return voPage;
    }

    /** 实体转展示对象：批量回填作者/门店，liked 由 Redis Set 判定（viewer 可空） */
    public List<BlogVO> toVOList(List<Review> reviews, Long viewerId) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = reviews.stream().map(Review::getUserId).collect(Collectors.toSet());
        Set<Long> storeIds = reviews.stream().map(Review::getStoreId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Store> storeMap = storeMapper.selectByIds(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        return reviews.stream().map(r -> {
            BlogVO vo = new BlogVO();
            BeanUtils.copyProperties(r, vo);
            if (r.getImages() != null && !r.getImages().isBlank()) {
                vo.setImages(List.of(r.getImages().split(",")));
            }
            User u = userMap.get(r.getUserId());
            vo.setUserName(u == null ? "" : u.getName());
            vo.setUserAvatar(u == null ? null : u.getAvatar());
            Store s = storeMap.get(r.getStoreId());
            vo.setStoreName(s == null ? "" : s.getName());
            vo.setLiked(viewerId != null && Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                    .isMember(LIKED_KEY_PREFIX + r.getId(), String.valueOf(viewerId))));
            return vo;
        }).collect(Collectors.toList());
    }
}
