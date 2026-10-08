package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * AI 服务推来的事件，Java 归一化后原样透传给前端（SSE 的 data 内容）。
 * <p>
 * 字段与 car-care-ai 的 app/schemas.py::ChatEvent 一一对应，两边改动必须同步。
 * <p>
 * 前端按 type 分支渲染：
 * start      会话开始（带 sessionId）
 * delta      正文增量，追加到当前气泡
 * reset      丢弃当前气泡已渲染的正文（模型在工具调用前说的过渡语，不该留在最终回答里）
 * tool_start 开始调用某工具（toolLabel 用于显示「正在查询门店…」）
 * tool_end   工具返回（toolOk=false 时前端可标红提示）
 * draft      预约草稿卡片
 * done       回答结束（带完整正文与 token/耗时统计）
 * error      出错，message 可直接展示
 */
@Data
@Schema(description = "AI 对话事件")
public class AiChatEventVO {

    @Schema(description = "事件类型", example = "delta")
    private String type;

    @Schema(description = "正文内容（delta/done）")
    private String content;

    @Schema(description = "会话 id（start/done）")
    private String sessionId;

    @Schema(description = "工具名")
    private String toolName;

    @Schema(description = "工具展示文案")
    private String toolLabel;

    @Schema(description = "工具入参")
    private Map<String, Object> toolArgs;

    @Schema(description = "工具是否成功")
    private Boolean toolOk;

    @Schema(description = "工具结果摘要")
    private String toolSummary;

    @Schema(description = "预约草稿")
    private Map<String, Object> draft;

    @Schema(description = "端到端耗时（毫秒）")
    private Integer latencyMs;

    @Schema(description = "输入 token 数")
    private Integer inputTokens;

    @Schema(description = "输出 token 数")
    private Integer outputTokens;

    @Schema(description = "错误信息（error）")
    private String message;
}
