package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 服务可用性：代理 car-care-ai 的 /healthz。
 * 前端可以用它决定是否展示 AI 入口，运维可以用它做探活。
 * AI 服务挂了不影响下单支付主链路，所以这里始终返回 200，用 available 字段表达状态。
 */
@Data
@Schema(description = "AI 服务可用性")
public class AiHealthVO {

    @Schema(description = "AI 服务是否可用")
    private boolean available;

    @Schema(description = "AI 服务自报状态：ok / degraded")
    private String status;

    @Schema(description = "使用的对话模型")
    private String model;

    @Schema(description = "RAG 是否就绪")
    private Boolean ragReady;

    @Schema(description = "知识库分片数")
    private Integer ragChunks;

    @Schema(description = "不可用时的原因")
    private String detail;

    public static AiHealthVO unavailable(String reason) {
        AiHealthVO vo = new AiHealthVO();
        vo.setAvailable(false);
        vo.setStatus("unavailable");
        vo.setDetail(reason);
        return vo;
    }
}
