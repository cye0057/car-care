package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券实体：支持满减券与代金券两类，stock 字段在阶段2改造为 Redis 预减库存
 */
@Data
@TableName("t_coupon")
public class Coupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private String title;
    private Integer type;
    private String typeDesc;
    private Integer stock;
    private BigDecimal minPrice;
    private BigDecimal discountPrice;
    private BigDecimal cashPrice;
    private String description;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
