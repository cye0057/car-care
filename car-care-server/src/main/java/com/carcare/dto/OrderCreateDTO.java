package com.carcare.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端下单请求：套餐单传 packageId，单项服务传 itemId+number，二选一
 */
@Data
@Schema(description = "下单请求")
public class OrderCreateDTO {

    @NotNull(message = "门店不能为空")
    @Schema(description = "门店 id")
    private Long storeId;

    @Schema(description = "车辆档案 id，可空")
    private Long vehicleId;

    @Schema(description = "套餐 id（与 itemId 二选一）")
    private Long packageId;

    @Schema(description = "保养项目 id（与 packageId 二选一）")
    private Long itemId;

    @Schema(description = "数量，默认 1")
    private Integer number = 1;

    @Schema(description = "预约到店时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-09-17 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentTime;

    @Schema(description = "备注/需求描述")
    private String remark;
}
