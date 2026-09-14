package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 套餐明细实体：记录套餐与服务项的关联，itemData 为下单时的价格快照
 */
@Data
@TableName("t_package_item")
public class PackageItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long packageId;
    private Long itemId;
    private String itemData;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
