package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 车辆档案请求：新增/修改共用，userId 由登录 token 决定不在此传
 */
@Data
@Schema(description = "车辆档案请求")
public class VehicleDTO {

    @NotBlank(message = "车牌号不能为空")
    @Schema(description = "车牌号", example = "浙A·12345")
    private String plateNumber;

    @Schema(description = "品牌", example = "大众")
    private String brand;

    @Schema(description = "型号", example = "迈腾 380TSI")
    private String model;

    @Schema(description = "颜色", example = "黑色")
    private String color;

    @Schema(description = "里程（km）", example = "62000")
    private Integer mileage;

    @Schema(description = "上牌日期", example = "2022-05-01")
    private LocalDate registerDate;

    @Schema(description = "下次保养日期", example = "2026-10-20")
    private LocalDate nextMaintainDate;
}
