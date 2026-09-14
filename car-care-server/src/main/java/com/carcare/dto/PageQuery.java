package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通用分页查询参数：各列表接口复用，name 字段在不同接口语义不同
 * （门店/项目=名称模糊，优惠券=标题模糊，订单=订单号模糊）
 */
@Data
@Schema(description = "通用分页查询参数")
public class PageQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "模糊搜索关键字（名称/标题/订单号，视接口而定）")
    private String name;

    @Schema(description = "状态过滤：1启用/2施工中…视接口而定，不传则不过滤")
    private Integer status;

    @Schema(description = "门店过滤，仅门店维度接口使用")
    private Long storeId;

    @Schema(description = "分类过滤，仅服务项接口使用")
    private Long categoryId;
}
