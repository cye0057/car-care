package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修工单实体：订单进入施工后生成，记录技师与进度，阶段5接入 WebSocket 提醒
 */
@Data
@TableName("t_work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long storeId;
    private String technician;
    private Integer status;
    private String progressDesc;
    private String finishImages;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
