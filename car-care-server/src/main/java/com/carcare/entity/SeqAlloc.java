package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 号段分配记录实体：一行代表一个业务的发号进度。
 * 每次取号段不是逐条 +1，而是 max_id += step 一次性预占一整段，
 * 把「每单一次 DB 写」摊薄成「每 step 单一次 DB 写」
 */
@Data
@TableName("t_seq_alloc")
public class SeqAlloc {
    @TableId(value = "biz_type", type = IdType.INPUT)
    private String bizType;
    private Long maxId;
    private Integer step;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
