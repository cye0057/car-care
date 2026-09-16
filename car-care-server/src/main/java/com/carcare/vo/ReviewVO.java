package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Feed/笔记列表展示对象：笔记主体 + 作者昵称 + 门店名 + 当前用户点赞态。
 * 作者与门店名通过批量查询回填，避免逐条查库
 */
@Data
@Schema(description = "笔记展示对象")
public class ReviewVO {
    private Long id;
    private Long storeId;
    @Schema(description = "门店名")
    private String storeName;
    private Long userId;
    @Schema(description = "作者昵称")
    private String userName;
    private Integer score;
    private String content;
    private String images;
    @Schema(description = "点赞数（Redis Set 实时计数）")
    private Integer likedCount;
    @Schema(description = "当前用户是否已点赞")
    private Boolean isLike;
    private LocalDateTime createTime;
}
