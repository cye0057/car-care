package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保养套餐实体：多个服务项的组合售卖，启用时要求明细项目均在售
 */
@Data
@TableName("t_package")
public class ServicePackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
