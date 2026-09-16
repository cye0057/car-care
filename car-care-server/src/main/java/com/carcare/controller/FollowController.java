package com.carcare.controller;

import com.carcare.common.BaseContext;
import com.carcare.common.Result;
import com.carcare.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 关注接口（车主侧）：Feed 推拉模式的开关由「被关注者粉丝数是否超阈值」决定
 */
@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
@Tag(name = "13-关注", description = "关注/取关")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetId}")
    @Operation(summary = "关注", description = "重复关注幂等成功")
    public Result<Void> follow(@PathVariable Long targetId) {
        followService.follow(BaseContext.getUserId(), targetId);
        return Result.success();
    }

    @DeleteMapping("/{targetId}")
    @Operation(summary = "取关")
    public Result<Void> unfollow(@PathVariable Long targetId) {
        followService.unfollow(BaseContext.getUserId(), targetId);
        return Result.success();
    }
}
