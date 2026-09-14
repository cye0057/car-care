package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 套餐新增/编辑请求：套餐基本信息 + 包含的服务项 id 列表。
 * 服务端会校验每个项目状态为在售，并生成 item_data 价格快照
 */
@Data
@Schema(description = "套餐保存请求")
public class PackageDTO {

    @Schema(description = "套餐 id，新增时为空")
    private Long id;

    @Schema(description = "所属门店 id")
    private Long storeId;

    @Schema(description = "套餐名称", example = "安心小保养套餐")
    private String name;

    @Schema(description = "套餐价格")
    private BigDecimal price;

    @Schema(description = "套餐说明")
    private String description;

    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

    @Schema(description = "包含的服务项目 id 列表，至少一个")
    private List<Long> itemIds;
}
