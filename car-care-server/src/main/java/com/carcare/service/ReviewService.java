package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BusinessException;
import com.carcare.common.CacheHelper;
import com.carcare.common.CacheKeys;
import com.carcare.dto.ReviewPublishDTO;
import com.carcare.entity.Order;
import com.carcare.entity.Review;
import com.carcare.entity.Store;
import com.carcare.entity.User;
import com.carcare.mapper.OrderMapper;
import com.carcare.mapper.ReviewMapper;
import com.carcare.mapper.StoreMapper;
import com.carcare.mapper.UserMapper;
import com.carcare.vo.BlogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * <p>
 * 订单评价闭环：从订单页进入时携带 orderId，校验「属于我 + 已完工」后写回 order_id，
 * 并把订单推进到已评价(6)；发布后重算门店均分，让 t_store.score 真实反映评价
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final StoreMapper storeMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final FollowService followService;
    private final CacheHelper cacheHelper;
    private final StringRedisTemplate stringRedisTemplate;

    public static final String FEED_INBOX_KEY = "feed:inbox:";
    public static final String LIKED_KEY_PREFIX = "like:blog:";

    /** 订单状态：已完工（可评价的前置状态） */
    private static final int ORDER_FINISHED = 4;

    /**
     * 发布笔记/评价：校验门店与订单归属 → 落库 → 推模式写扩散 → 重算门店评分。
     * 事务覆盖「写评价 + 流转订单 + 更新门店评分」，任一步失败整体回滚
     */
    @Transactional
    public Long publish(Long userId, ReviewPublishDTO dto) {
        Store store = storeMapper.selectById(dto.getStoreId());
        if (store == null) {
            throw new BusinessException("门店不存在");
        }
        Order order = null;
        if (dto.getOrderId() != null) {
            order = orderMapper.selectById(dto.getOrderId());
            if (order == null || !order.getUserId().equals(userId)) {
                throw new BusinessException("订单不存在或无权评价");
            }
            if (!order.getStoreId().equals(dto.getStoreId())) {
                throw new BusinessException("订单与门店不匹配");
            }
            if (order.getStatus() == 6) {
                throw new BusinessException("该订单已评价过");
            }
            if (order.getStatus() != ORDER_FINISHED) {
                throw new BusinessException("订单尚未完工，暂不能评价");
            }
        }
        Review review = new Review();
        review.setStoreId(dto.getStoreId());
        review.setOrderId(dto.getOrderId());
        review.setScore(dto.getScore());
        review.setContent(dto.getContent());
        review.setImages(dto.getImages());
        review.setUserId(userId);
        review.setLikedCount(0);
        reviewMapper.insert(review);

        if (order != null) {
            // 4→6 由状态机校验合法性，此处已是完工态，流转必然成功
            orderService.changeStatus(order.getId(), 6, null);
        }
        refreshStoreScore(dto.getStoreId());
        pushToFollowers(review);
        return review.getId();
    }

    /**
     * 重算门店均分并回写 t_store.score。
     * 门店详情走的是逻辑过期缓存，改分后必须双删，否则首页仍显示旧评分
     */
    private void refreshStoreScore(Long storeId) {
        BigDecimal avg = reviewMapper.avgScoreByStore(storeId);
        if (avg == null) {
            return; // 理论上刚插入一条评价，AVG 不会为空；防御性返回
        }
        Store update = new Store();
        update.setId(storeId);
        update.setScore(avg);
        storeMapper.updateById(update);
        cacheHelper.deleteWithDoubleDelete(CacheKeys.storeDetail(storeId), CacheKeys.STORE_LIST_ENABLED);
        log.debug("门店 {} 评分重算为 {}", storeId, avg);
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
