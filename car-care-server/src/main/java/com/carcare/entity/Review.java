package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店评价/养车笔记实体：liked_count 为计数快照，
 * 点赞明细由 Redis Set 维护（阶段4 Feed 流使用），deleted 逻辑删除
 */
@Data
@TableName("t_review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private Long userId;
    private Long orderId;
    private Integer score;
    private String content;
    private String images;
    private Integer likedCount;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
