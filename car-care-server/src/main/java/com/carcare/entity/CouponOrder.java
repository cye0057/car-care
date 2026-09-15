package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领券记录实体：(couponId,userId,type) 唯一索引防止同一用户重复领取，
 * 秒杀场景下该索引是 Redis 预减之后的第二道防重复闸门。
 * id 由号段发号器生成（SegmentIdService），不使用 DB 自增
 */
@Data
@TableName("t_coupon_order")
public class CouponOrder {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long couponId;
    private Long userId;
    private Integer type;
    private Integer status;
    private Long orderId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
