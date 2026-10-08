package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BusinessException;
import com.carcare.config.AiProperties;
import com.carcare.dto.AiUpstreamChatDTO;
import com.carcare.entity.AiConversation;
import com.carcare.entity.AiMessage;
import com.carcare.mapper.AiConversationMapper;
import com.carcare.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * AI 会话与消息的持久化。
 * <p>
 * Java 是会话的唯一数据源，Python 侧完全无状态。这样做换来三个好处：
 * 1) 会话归属校验直接复用 userId，不存在「AI 服务记错用户」的可能；
 * 2) Python 可以随时重启、也能水平扩容，不需要会话粘性或外部 checkpointer；
 * 3) 会话记录留在业务库里，后台可以直接查、可以做效果评估，不用再去 Python 侧捞。
 * 代价是每轮对话多一次「读历史」的查询——用 sessionId 上的索引可以忽略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationService {

    /** 会话标题截断长度：列表里只展示一行，存全文没意义 */
    private static final int TITLE_MAX = 24;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiProperties aiProperties;

    /**
     * 解析 sessionId：为空则新建会话；不为空则校验归属。
     * 归属校验放在这里而不是各调用点，避免漏掉某个接口造成越权读他人对话。
     */
    public String resolveSessionId(Long userId, String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            String generated = UUID.randomUUID().toString().replace("-", "");
            createConversation(userId, generated);
            return generated;
        }
        AiConversation existing = conversationMapper.selectOne(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getSessionId, sessionId));
        if (existing == null) {
            createConversation(userId, sessionId);
            return sessionId;
        }
        if (!existing.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return sessionId;
    }

    private void createConversation(Long userId, String sessionId) {
        AiConversation conversation = new AiConversation();
        conversation.setSessionId(sessionId);
        conversation.setUserId(userId);
        conversation.setTitle("");
        conversation.setMessageCount(0);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
    }

    /** 校验会话归属，用于历史查询/删除这类只带 sessionId 的接口 */
    public AiConversation mustOwn(Long userId, String sessionId) {
        AiConversation conversation = conversationMapper.selectOne(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getSessionId, sessionId));
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return conversation;
    }

    public void appendUserMessage(Long userId, String sessionId, String content) {
        insertMessage(userId, sessionId, "user", content, null, null, null, null);
        touchConversation(sessionId, content);
    }

    public void appendAssistantMessage(Long userId, String sessionId, String content,
                                       String toolTrace, Integer inputTokens,
                                       Integer outputTokens, Integer latencyMs) {
        insertMessage(userId, sessionId, "assistant", content, toolTrace, inputTokens, outputTokens, latencyMs);
        touchConversation(sessionId, null);
    }

    private void insertMessage(Long userId, String sessionId, String role, String content, String toolTrace,
                               Integer inputTokens, Integer outputTokens, Integer latencyMs) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setToolTrace(toolTrace);
        message.setInputTokens(inputTokens);
        message.setOutputTokens(outputTokens);
        message.setLatencyMs(latencyMs);
        messageMapper.insert(message);
    }

    /** 会话的标题只在首条消息时写入，之后只更新计数与时间 */
    private void touchConversation(String sessionId, String firstUserContent) {
        AiConversation conversation = conversationMapper.selectOne(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getSessionId, sessionId));
        if (conversation == null) {
            return;
        }
        conversation.setMessageCount((conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 1);
        conversation.setLastMessageAt(LocalDateTime.now());
        if (StringUtils.hasText(firstUserContent)
                && !StringUtils.hasText(conversation.getTitle())) {
            conversation.setTitle(truncate(firstUserContent));
        }
        conversationMapper.updateById(conversation);
    }

    /**
     * 取最近若干轮历史，按时间正序返回。
     * <p>
     * 两条修剪规则（都是为了让下发的消息序列始终是「user 开头、assistant 结尾」的合法交替）：
     * 1) 去掉开头的孤儿 assistant——窗口被 maxMessages 截断时可能只留下回答；
     * 2) 去掉结尾的孤儿 user——上一次对话如果失败，用户消息落库了但没有回答，
     *    留着会和本次的新问题连成两条 user 消息。
     */
    public List<AiUpstreamChatDTO.Turn> loadHistory(String sessionId) {
        int limit = Math.max(aiProperties.getHistoryRounds(), 1) * 2;
        List<AiMessage> recent = messageMapper.selectPage(new Page<>(1, limit),
                Wrappers.<AiMessage>lambdaQuery()
                        .eq(AiMessage::getSessionId, sessionId)
                        .orderByDesc(AiMessage::getId)).getRecords();
        if (recent.isEmpty()) {
            return List.of();
        }
        List<AiMessage> ordered = new ArrayList<>(recent);
        Collections.reverse(ordered);

        while (!ordered.isEmpty() && !"user".equals(ordered.get(0).getRole())) {
            ordered.remove(0);
        }
        while (!ordered.isEmpty() && !"assistant".equals(ordered.get(ordered.size() - 1).getRole())) {
            ordered.remove(ordered.size() - 1);
        }

        List<AiUpstreamChatDTO.Turn> history = new ArrayList<>(ordered.size());
        for (AiMessage message : ordered) {
            AiUpstreamChatDTO.Turn turn = new AiUpstreamChatDTO.Turn();
            turn.setRole(message.getRole());
            turn.setContent(message.getContent());
            history.add(turn);
        }
        return history;
    }

    public Page<AiConversation> pageByUser(Long userId, Integer pageNum, Integer pageSize) {
        return conversationMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<AiConversation>lambdaQuery()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getLastMessageAt));
    }

    /** 会话消息（含工具轨迹）：toolTrace 是 JSON 字符串，前端按需解析回放 */
    public List<AiMessage> messages(Long userId, String sessionId) {
        mustOwn(userId, sessionId);
        return messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getId));
    }

    @Transactional
    public void delete(Long userId, String sessionId) {
        mustOwn(userId, sessionId);
        conversationMapper.delete(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getSessionId, sessionId));
        messageMapper.delete(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getSessionId, sessionId));
    }

    /** 对某条回答点赞/点踩；只能评价自己的、且只能是 assistant 消息 */
    public void feedback(Long userId, Long messageId, Integer feedback) {
        AiMessage message = messageMapper.selectById(messageId);
        if (message == null || !message.getUserId().equals(userId)) {
            throw new BusinessException("消息不存在或无权操作");
        }
        if (!"assistant".equals(message.getRole())) {
            throw new BusinessException("只能评价 AI 的回答");
        }
        message.setFeedback(feedback);
        messageMapper.updateById(message);
    }

    private String truncate(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= TITLE_MAX ? flat : flat.substring(0, TITLE_MAX) + "…";
    }
}
