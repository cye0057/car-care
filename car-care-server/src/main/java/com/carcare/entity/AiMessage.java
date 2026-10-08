package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 消息实体：只存 user 与 assistant 两类。
 * <p>
 * 工具调用过程不单独建表，而是以 JSON 塞进 assistant 消息的 toolTrace 字段——
 * 它只在「回放这段回答是怎么来的」时才有用，独立建表会让联表查询变复杂而收益很小。
 * <p>
 * inputTokens/outputTokens/latencyMs 是每次回答的成本与耗时快照，
 * 攒起来就能回答「AI 模块一个月花了多少 token、慢在哪些问题上」。
 */
@Data
@TableName("t_ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    /** user / assistant */
    private String role;

    private String content;

    /** 工具调用轨迹 JSON 数组：[{name,label,args,ok,summary}]，仅 assistant 消息有值 */
    private String toolTrace;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer latencyMs;

    /** 1 赞 / 2 踩，仅 assistant 消息可被评价 */
    private Integer feedback;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
