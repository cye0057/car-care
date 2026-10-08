package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 套餐的 AI 视图：把 t_package_item 的 JSON 快照里的项目名拍平成一个字符串，
 * 让 AI 一眼能看出套餐包含什么（模型不需要也不该理解明细表结构）
 */
@Data
@Schema(description = "AI 内部接口用的套餐视图")
public class AiPackageVO {

    @Schema(description = "套餐 id")
    private Long id;

    @Schema(description = "所属门店 id")
    private Long storeId;

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "套餐价格")
    private BigDecimal price;

    @Schema(description = "套餐说明")
    private String description;

    @Schema(description = "包含的项目名，顿号分隔")
    private List<String> itemNames;
}
