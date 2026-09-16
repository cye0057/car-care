package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注关系实体：user_id 关注 follow_user_id。
 * 唯一索引 (user_id, follow_user_id) 防重复关注，
 * 粉丝数=按 follow_user_id 计数（判断大账号阈值用）
 */
@Data
@TableName("t_follow")
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long followUserId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
