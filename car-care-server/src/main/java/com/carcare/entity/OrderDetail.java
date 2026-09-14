package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体：记录下单时的项目名称与价格快照，不受后续改价影响
 */
@Data
@TableName("t_order_detail")
public class OrderDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long itemId;
    private String itemName;
    private String image;
    private BigDecimal unitPrice;
    private Integer number;
    private BigDecimal amount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
