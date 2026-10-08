package com.carcare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手配置：由 application.yml 的 carcare.ai 前缀绑定。
 * <p>
 * 超时预算的分层关系是刻意设计的：
 * connect(3s) ≪ 首字等待 ≪ stream(180s)，且 Python 侧自己的单请求预算（REQUEST_TIMEOUT，默认 120s）
 * 必须小于 stream-timeout-ms——让上游先认输并给出友好提示，而不是下游先断流、
 * 上游还在继续烧 token（这是 travel 项目踩过的坑）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "carcare.ai")
public class AiProperties {

    /** AI 助手总开关：关闭后 /api/ai/** 直接返回友好提示，不影响主链路 */
    private boolean enabled = true;

    /** Python FastAPI 服务地址，仅本机/内网可达 */
    private String baseUrl = "http://127.0.0.1:8000";

    /** Java → Python 的内部调用令牌，需与 Python 侧 CARCARE_INTERNAL_TOKEN 一致 */
    private String internalToken;

    /** 建连超时：AI 服务在本机，超过 3 秒说明它没起来，没必要再等 */
    private int connectTimeoutMs = 3000;

    /** 流式响应总超时：必须大于 Python 的单请求预算 */
    private int streamTimeoutMs = 180000;

    /** 每次对话下发给 AI 的历史轮数（一轮 = 一问一答） */
    private int historyRounds = 6;

    /** 用户上下文里带几笔近期订单 */
    private int contextOrderLimit = 5;

    /** 用户上下文里带几台车 */
    private int contextVehicleLimit = 5;

    /** 用户上下文里带几张已领取的优惠券 */
    private int contextCouponLimit = 10;

    /** 每笔订单带几个项目明细 */
    private int contextOrderItemLimit = 3;
}
