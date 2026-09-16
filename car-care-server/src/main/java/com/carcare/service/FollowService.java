package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.common.BusinessException;
import com.carcare.entity.Follow;
import com.carcare.entity.User;
import com.carcare.mapper.FollowMapper;
import com.carcare.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关注关系服务（阶段4 Feed 流的基础设施）。
 * 大账号判定：粉丝数超过阈值 carcare.feed.push-follower-threshold 的作者发帖不再写扩散，
 * 改为粉丝刷 Feed 时实时拉取——写扩散成本 O(粉丝数) 与读扩散成本 O(关注大账号数) 的动态切换
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    @org.springframework.beans.factory.annotation.Value("${carcare.feed.push-follower-threshold:3000}")
    private long pushThreshold;

    public void follow(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new BusinessException("不能关注自己");
        }
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        Follow exist = followMapper.selectOne(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, targetId));
        if (exist != null) {
            return; // 幂等：重复关注直接成功
        }
        Follow f = new Follow();
        f.setUserId(userId);
        f.setFollowUserId(targetId);
        followMapper.insert(f);
    }

    public void unfollow(Long userId, Long targetId) {
        followMapper.delete(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, targetId));
    }

    /** userId 关注的人（Feed 拉模式取作者集合） */
    public List<Long> followingIds(Long userId) {
        return followMapper.selectList(Wrappers.<Follow>lambdaQuery()
                        .eq(Follow::getUserId, userId))
                .stream().map(Follow::getFollowUserId).toList();
    }

    /** userId 的粉丝（发帖推模式取投递对象） */
    public List<Long> followerIds(Long userId) {
        return followMapper.selectList(Wrappers.<Follow>lambdaQuery()
                        .eq(Follow::getFollowUserId, userId))
                .stream().map(Follow::getUserId).toList();
    }

    /** 是否大账号（发帖走拉模式） */
    public boolean isBigAccount(Long userId) {
        Long cnt = followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowUserId, userId));
        return cnt > pushThreshold;
    }
}
