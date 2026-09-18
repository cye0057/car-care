package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布养车笔记/门店评价请求：绑定门店（消费后可评）+ 评分 + 图文内容。
 * orderId 可空——从订单页进入评价时携带，用于校验「这笔订单确实属于我且已完工」，
 * 并在发布成功后把订单流转到已评价
 */
@Data
@Schema(description = "发布笔记请求")
public class ReviewPublishDTO {

    @NotNull(message = "门店不能为空")
    @Schema(description = "关联门店 id")
    private Long storeId;

    @Schema(description = "关联订单 id，从订单页进入评价时携带；晒图笔记可不传")
    private Long orderId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 分")
    @Max(value = 5, message = "评分最高 5 分")
    @Schema(description = "评分 1-5", example = "5")
    private Integer score;

    @NotBlank(message = "内容不能为空")
    @Size(max = 1000, message = "评价内容最多 1000 字")
    @Schema(description = "笔记正文")
    private String content;

    @Size(max = 1024, message = "图片地址过长")
    @Schema(description = "图片URL，逗号分隔")
    private String images;
}
