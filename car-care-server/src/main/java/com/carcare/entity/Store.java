package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 维修门店实体：含经纬度坐标，后续用于 GEO 附近门店查询与缓存改造
 */
@Data
@TableName("t_store")
public class Store {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private String city;
    private BigDecimal lng;
    private BigDecimal lat;
    private String phone;
    private BigDecimal score;
    private String commentScores;
    private String cover;
    private String businessHours;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
