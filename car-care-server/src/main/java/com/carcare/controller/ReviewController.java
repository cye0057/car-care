package com.carcare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BaseContext;
import com.carcare.common.Result;
import com.carcare.dto.ReviewPublishDTO;
import com.carcare.entity.Review;
import com.carcare.service.FeedService;
import com.carcare.service.ReviewService;
import com.carcare.vo.BlogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 笔记/评价接口（车主侧）：发布、点赞、门店评价列表、我的笔记
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "12-养车笔记", description = "发布/点赞/门店评价")
public class ReviewController {

    private final ReviewService reviewService;
    private final FeedService feedService;

    @PostMapping
    @Operation(summary = "发布笔记/评价",
            description = "带 orderId 走订单评价闭环：校验归属与完工态，发布后订单流转 4→6 并重算门店评分；"
                    + "不带 orderId 即普通晒图笔记")
    public Result<Long> publish(@RequestBody @Valid ReviewPublishDTO dto) {
        return Result.success(reviewService.publish(BaseContext.getUserId(), dto));
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "点赞", description = "Redis Set 去重，重复点赞幂等返回 false")
    public Result<Boolean> like(@PathVariable Long id) {
        return Result.success(reviewService.like(id, BaseContext.getUserId()));
    }

    @DeleteMapping("/{id}/like")
    @Operation(summary = "取消点赞")
    public Result<Boolean> unlike(@PathVariable Long id) {
        return Result.success(reviewService.unlike(id, BaseContext.getUserId()));
    }

    @GetMapping("/store/{storeId}")
    @Operation(summary = "门店评价分页")
    public Result<Page<BlogVO>> pageByStore(@PathVariable Long storeId,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(reviewService.pageByStore(storeId, pageNum, pageSize));
    }

    @GetMapping("/my")
    @Operation(summary = "我的笔记列表")
    public Result<List<Review>> my() {
        return Result.success(feedService.myReviews(BaseContext.getUserId()));
    }
}
