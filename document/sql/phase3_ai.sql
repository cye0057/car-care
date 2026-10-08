-- ============================================================
-- 阶段 8：AI 养车顾问模块
-- 说明：会话与消息由 Java 侧持久化（Python AI 服务完全无状态），
--       所以这两张表是 AI 模块的唯一数据源。
-- 执行：mysql -uroot -p car_care < document/sql/phase3_ai.sql
-- ============================================================

-- AI 会话表：一个 sessionId 一条记录
CREATE TABLE IF NOT EXISTS t_ai_conversation
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id      VARCHAR(64)  NOT NULL COMMENT '会话 id，服务端生成的 uuid（去横线）',
    user_id         BIGINT       NOT NULL COMMENT '归属车主，所有读写都要校验',
    title           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '会话标题，取首条提问前 24 字',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息条数（含提问与回答）',
    last_message_at DATETIME     NULL COMMENT '最后一条消息时间，会话列表按它倒序',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_user_last_message (user_id, last_message_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 会话表';

-- AI 消息表：只存 user 与 assistant 两类；工具调用轨迹以 JSON 存在 assistant 消息上
CREATE TABLE IF NOT EXISTS t_ai_message
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id    VARCHAR(64) NOT NULL COMMENT '所属会话',
    user_id       BIGINT      NOT NULL COMMENT '归属车主',
    role          VARCHAR(16) NOT NULL COMMENT 'user / assistant',
    content       TEXT        NOT NULL COMMENT '消息正文',
    tool_trace    JSON        NULL COMMENT '工具调用轨迹 JSON 数组，仅 assistant 消息有值，用于回放',
    input_tokens  INT         NULL COMMENT '本次回答的输入 token 数',
    output_tokens INT         NULL COMMENT '本次回答的输出 token 数',
    latency_ms    INT         NULL COMMENT '本次回答端到端耗时（毫秒）',
    feedback      TINYINT     NULL COMMENT '效果反馈：1 赞 2 踩，NULL 表示未评价',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id, id),
    KEY idx_user_time (user_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 消息表';
