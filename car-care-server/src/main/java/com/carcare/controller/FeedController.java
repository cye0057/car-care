package com.carcare.controller;

import com.carcare.common.BaseContext;
import com.carcare.common.Result;
import com.carcare.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Feed 流接口（车主侧）：收件箱（推）+ 大账号（拉）合并，双游标滚动分页。
 * 首页不传参；下一页把响应里的 nextMaxId/nextBeginTime 原样回传
 */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Tag(name = "14-Feed流", description = "关注动态（推拉结合+滚动分页）")
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    @Operation(summary = "关注Feed流", description = "游标分页：maxId/beginTime 由上一页响应回传，每页5条")
    public Result<Map<String, Object>> feed(@RequestParam(required = false) Long maxId,
                                            @RequestParam(required = false) Long beginTime) {
        return Result.success(feedService.queryFeed(BaseContext.getUserId(), maxId, beginTime));
    }
}
