package com.carcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话实体：一个 sessionId 一条记录。
 * <p>
 * 会话与消息的持久化放在 Java 侧而不是 Python 侧，是因为 Java 已经有完整的
 * 用户体系与鉴权链路：会话归属校验可以直接复用 userId，
 * AI 服务则保持无状态（可随时重启、可水平扩容，不依赖任何本地存储）。
 */
@Data
@TableName("t_ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 id：由服务端生成的 UUID，对前端可见，作为会话的唯一标识 */
    private String sessionId;

    /** 归属用户：所有读写都要校验，防止越权查看他人对话 */
    private Long userId;

    /** 会话标题：取首条用户提问的前若干字，仅用于列表展示 */
    private String title;

    private Integer messageCount;

    private LocalDateTime lastMessageAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
