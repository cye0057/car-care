package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保养服务分类实体：门店项目的分类维度（如发动机与机油、轮胎与轮毂）
 */
@Data
@TableName("t_service_category")
public class ServiceCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
