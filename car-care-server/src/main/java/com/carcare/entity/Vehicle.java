package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 车辆档案实体：车主绑定的车辆信息，含下次保养日期用于定时提醒
 */
@Data
@TableName("t_vehicle")
public class Vehicle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String plateNumber;
    private String brand;
    private String model;
    private String color;
    private Integer mileage;
    private LocalDate registerDate;
    private LocalDate nextMaintainDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
