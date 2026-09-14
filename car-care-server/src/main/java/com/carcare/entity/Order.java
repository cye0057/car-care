package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务订单实体：状态机 1待支付-2已支付-3施工中-4已完工-5已取消-6已评价
 */
@Data
@TableName("t_order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long storeId;
    private Long vehicleId;
    private Long packageId;
    private Long couponOrderId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal actualAmount;
    private Integer status;
    private Integer payType;
    private Integer payStatus;
    private String remark;
    private LocalDateTime appointmentTime;
    private LocalDateTime orderTime;
    private LocalDateTime checkoutTime;
    private String cancelReason;
    private LocalDateTime cancelTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
