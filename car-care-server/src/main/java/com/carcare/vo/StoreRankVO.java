package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 附近门店/热榜展示对象：门店基础信息 + 距离（GEO 查询）或热度分（榜单查询）
 */
@Data
@Schema(description = "附近门店/热榜项")
public class StoreRankVO {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private BigDecimal score;
    @Schema(description = "距查询点距离（km），附近查询时返回")
    private Double distanceKm;
    @Schema(description = "热度分，榜单查询时返回")
    private Double hotScore;
}
