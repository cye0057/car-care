package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feed 流/笔记展示对象：笔记内容 + 作者与门店冗余信息 + 点赞状态
 */
@Data
@Schema(description = "养车笔记/Feed项")
public class BlogVO {
    private Long id;
    @Schema(description = "作者昵称")
    private String userName;
    @Schema(description = "作者头像")
    private String userAvatar;
    private Long storeId;
    @Schema(description = "关联门店名")
    private String storeName;
    @Schema(description = "评分 1-5")
    private Integer score;
    private String content;
    @Schema(description = "图片列表（DB 逗号分隔存储，返回时拆分）")
    private List<String> images;
    @Schema(description = "当前用户是否已点赞")
    private Boolean liked;
    @Schema(description = "点赞数")
    private Integer likedCount;
    private LocalDateTime createTime;
}
