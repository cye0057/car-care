package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 下发给 AI 服务的用户上下文。
 * <p>
 * 这是「AI 服务不持有用户态」这条设计的落点：Java 在收到对话请求时，用已通过 JWT 校验的
 * userId 把该用户的私有数据查出来、裁剪成精简视图，随请求一起发给 Python。
 * 好处有三：
 * 1) AI 服务无法反查他人数据——它连 userId 查询入口都没有，越权在结构上就不成立；
 * 2) Python 侧不需要连业务库，也就不需要数据库凭证；
 * 3) 上下文体积可控（车辆 5 台、订单 5 笔、每笔 3 个明细），不会把 prompt 撑爆。
 * <p>
 * 内层视图用静态嵌套类而不是拆成 7 个文件：它们只在本次下发中一起出现，
 * 放在一起更容易看出「下发了哪些数据」这个整体契约。
 * <p>
 * 时间字段一律用**已格式化好的 String**（yyyy-MM-dd / yyyy-MM-dd HH:mm），不用 LocalDateTime：
 * - Python 侧的 pydantic 模型声明就是 str，契约上必须一致；
 * - 模型直接读 "2026-09-15 10:00" 比读 "2026-09-15T10:00:00" 更不容易理解错；
 * - 最关键的是避开一个坑：WebClient 若用默认 ObjectMapper 序列化，LocalDateTime 会变成
 *   [2026,9,15,10,0] 这样的数组（WRITE_DATES_AS_TIMESTAMPS 默认开启），
 *   Python 侧直接 422。用 String 后这类配置差异就影响不到接口契约了。
 */
@Data
@Schema(description = "下发给 AI 的用户上下文")
public class AiUserContextVO {

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "用户昵称")
    private String name;

    @Schema(description = "常用城市，用于默认推荐门店")
    private String city;

    private List<Vehicle> vehicles;
    private List<Order> recentOrders;
    private List<WorkOrder> workOrders;
    private List<Coupon> coupons;

    /** 车辆档案：给出保养建议的核心依据 */
    @Data
    @Schema(description = "车辆档案精简视图")
    public static class Vehicle {
        private Long id;
        private String plateNumber;
        private String brand;
        private String model;
        private String color;
        private Integer mileage;
        @Schema(description = "注册日期 yyyy-MM-dd")
        private String registerDate;
        @Schema(description = "下次保养日期 yyyy-MM-dd")
        private String nextMaintainDate;
    }

    /** 订单明细快照 */
    @Data
    @Schema(description = "订单明细精简视图")
    public static class OrderItem {
        private String itemName;
        private Integer number;
        private BigDecimal amount;
    }

    /** 近期订单：回答「我上次那个单子」的依据 */
    @Data
    @Schema(description = "订单精简视图")
    public static class Order {
        private String orderNo;
        private Long storeId;
        private String storeName;
        private Integer status;
        /** 状态中文名，直接在 Java 侧翻译好，省得模型去猜数字含义 */
        private String statusText;
        private BigDecimal totalAmount;
        private BigDecimal actualAmount;
        @Schema(description = "预约到店时间 yyyy-MM-dd HH:mm")
        private String appointmentTime;
        @Schema(description = "下单时间 yyyy-MM-dd HH:mm")
        private String orderTime;
        private List<OrderItem> items;
    }

    /** 工单进度：回答「我的车修到哪一步了」的依据 */
    @Data
    @Schema(description = "工单进度精简视图")
    public static class WorkOrder {
        private String orderNo;
        private Long storeId;
        private Integer status;
        private String statusText;
        private String technician;
        private String progressDesc;
        @Schema(description = "开工时间 yyyy-MM-dd HH:mm")
        private String startTime;
        @Schema(description = "完工时间 yyyy-MM-dd HH:mm")
        private String finishTime;
    }

    /** 用户已领取的优惠券 */
    @Data
    @Schema(description = "已领取优惠券精简视图")
    public static class Coupon {
        private Long couponId;
        private Long storeId;
        private String title;
        private Integer type;
        private String typeDesc;
        private BigDecimal minPrice;
        private BigDecimal discountPrice;
        private BigDecimal cashPrice;
        @Schema(description = "有效期截止 yyyy-MM-dd HH:mm")
        private String validEndTime;
    }
}
