package com.carcare.vo;

import com.carcare.entity.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单列表展示对象：在订单实体基础上冗余门店名/车主名/车牌号，
 * 由 Service 层批量查询回填，避免前端逐条再查
 */
@Data
@Schema(description = "订单列表项（含关联名称）")
public class OrderVO extends Order {

    @Schema(description = "门店名称")
    private String storeName;

    @Schema(description = "车主姓名")
    private String userName;

    @Schema(description = "车牌号")
    private String plateNumber;
}
