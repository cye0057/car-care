package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保养/维修项目实体：门店的最小可售卖服务单元，status 控制起售停售
 */
@Data
@TableName("t_service_item")
public class ServiceItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private Long categoryId;
    private String name;
    private BigDecimal price;
    private String description;
    private String image;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
