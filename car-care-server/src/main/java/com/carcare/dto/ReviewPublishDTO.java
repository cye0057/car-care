package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发布养车笔记请求：绑定门店（消费后可评）+ 评分 + 图文内容
 */
@Data
@Schema(description = "发布笔记请求")
public class ReviewPublishDTO {

    @NotNull(message = "门店不能为空")
    @Schema(description = "关联门店 id")
    private Long storeId;

    @NotNull(message = "评分不能为空")
    @Schema(description = "评分 1-5", example = "5")
    private Integer score;

    @NotBlank(message = "内容不能为空")
    @Schema(description = "笔记正文")
    private String content;

    @Schema(description = "图片URL，逗号分隔")
    private String images;
}
