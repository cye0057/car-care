package com.carcare.service;

import com.carcare.client.AiClient;
import com.carcare.common.BaseContext;
import com.carcare.config.AiProperties;
import com.carcare.dto.AiChatDTO;
import com.carcare.dto.AiUpstreamChatDTO;
import com.carcare.vo.AiChatEventVO;
import com.carcare.vo.AiUserContextVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 对话编排：组装上下文与历史 → 调 Python 流式接口 → 逐帧透传给前端 → 收尾落库。
 * <p>
 * 三个关键点：
 * <p>
 * 1. **逐帧透传而不是攒完再发**。上游每来一帧就转给前端，所以首字延迟等于模型首 token 时间。
 * （对比 travel 项目：那边把整段回答缓冲完才分片推送，首字延迟等于完整生成时间。）
 * <p>
 * 2. **副作用靠解析帧累积**。回答正文、工具轨迹、token 用量都从透传的帧里顺手收集，
 * 不额外维护一份状态，避免「转发的内容」和「落库的内容」不一致。
 * <p>
 * 3. **落库放在 doFinally + 专用线程池**。doFinally 在流终止（完成/出错/客户端断开）时都会执行，
 * 保证「用户问过的问题一定留痕」；JDBC 是阻塞操作，丢到 aiDbExecutor 上跑，
 * 不占用 Reactor 事件循环线程。
 */
@Slf4j
@Service
public class AiChatService {

    private final AiProperties aiProperties;
    private final AiClient aiClient;
    private final AiContextService aiContextService;
    private final AiConversationService aiConversationService;
    private final ObjectMapper objectMapper;
    private final ExecutorService dbExecutor;

    public AiChatService(AiProperties aiProperties,
                         AiClient aiClient,
                         AiContextService aiContextService,
                         AiConversationService aiConversationService,
                         ObjectMapper objectMapper,
                         @Qualifier("aiDbExecutor") ExecutorService dbExecutor) {
        this.aiProperties = aiProperties;
        this.aiClient = aiClient;
        this.aiContextService = aiContextService;
        this.aiConversationService = aiConversationService;
        this.objectMapper = objectMapper;
        this.dbExecutor = dbExecutor;
    }

    public Flux<String> chat(AiChatDTO dto) {
        if (!aiProperties.isEnabled()) {
            return Flux.just(frame(errorEvent("AI 助手当前未开启")));
        }
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Flux.just(frame(errorEvent("请先登录后再使用 AI 助手")));
        }

        // 1) 会话：为空则新建；带 sessionId 时校验归属，防止越权写他人会话
        String sessionId = aiConversationService.resolveSessionId(userId, dto.getSessionId());
        // 2) 历史必须在写入本轮用户消息之前读，否则会把当前问题也当成历史重复下发
        List<AiUpstreamChatDTO.Turn> history = aiConversationService.loadHistory(sessionId);
        // 3) 用户上下文：用已鉴权的 userId 查，不接受请求体传入的身份
        AiUserContextVO userContext = aiContextService.build(userId);

        AiUpstreamChatDTO payload = new AiUpstreamChatDTO();
        payload.setSessionId(sessionId);
        payload.setMessage(dto.getMessage());
        payload.setUserId(userId);
        payload.setHistory(history);
        payload.setUserContext(userContext);

        aiConversationService.appendUserMessage(userId, sessionId, dto.getMessage());

        // 从透传的帧里累积落库所需的副作用
        StringBuilder answer = new StringBuilder();
        List<String> toolTrace = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Integer> latency = new AtomicReference<>();
        AtomicReference<Integer> inTokens = new AtomicReference<>();
        AtomicReference<Integer> outTokens = new AtomicReference<>();

        Flux<String> start = Flux.just(frame(startEvent(sessionId)));

        Flux<String> body = aiClient.chatStream(payload)
                .map(this::parse)
                .filter(java.util.Objects::nonNull)
                // 丢掉 Python 的 start：Java 已经在最前面发过一个带 sessionId 的 start，
                // 两个 start 会让前端拿到一个 sessionId 为 null 的重复事件
                .filter(event -> !"start".equals(event.getType()))
                .doOnNext(event -> accumulate(event, answer, toolTrace, latency, inTokens, outTokens))
                .map(this::frame)
                .onErrorResume(e -> {
                    log.warn("AI 流式对话失败 session={}", sessionId, e);
                    return Flux.just(frame(errorEvent(friendlyMessage(e))));
                });

        // 无论正常结束、报错还是客户端断开，都落库一次，保证「问过的问题一定留痕」
        Flux<String> persisted = body.doFinally(signal -> persistAsync(
                userId, sessionId, signal, answer.toString(), toolTrace, latency, inTokens, outTokens));

        return Flux.concat(start, persisted);
    }

    /** 累积落库所需的副作用。reset 表示这一轮是工具轮的过渡语，必须清掉 */
    private void accumulate(AiChatEventVO event, StringBuilder answer, List<String> trace,
                            AtomicReference<Integer> latency, AtomicReference<Integer> inTokens,
                            AtomicReference<Integer> outTokens) {
        switch (event.getType()) {
            case "delta" -> {
                if (event.getContent() != null) {
                    answer.append(event.getContent());
                }
            }
            case "reset" -> answer.setLength(0);
            case "tool_end" -> trace.add(toJson(toolTraceEntry(event)));
            case "draft" -> trace.add(toJson(draftTraceEntry(event)));
            case "done" -> {
                if (StringUtils.hasText(event.getContent())) {
                    // done 带的是模型给出的完整回答，比逐帧拼接更权威（拼接可能漏掉 reset 边界）
                    answer.setLength(0);
                    answer.append(event.getContent());
                }
                latency.set(event.getLatencyMs());
                inTokens.set(event.getInputTokens());
                outTokens.set(event.getOutputTokens());
            }
            default -> {
                // start / tool_start 不需要累积
            }
        }
    }

    /**
     * 工具轨迹的紧凑结构。
     * 只存回放真正需要的字段——直接把整个事件对象序列化进去会带上十来个 null 字段，
     * 一条轨迹几十字节的信息会膨胀成几百字节，还会随事件契约变更而变化，不适合当持久化格式。
     */
    private Map<String, Object> toolTraceEntry(AiChatEventVO event) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("kind", "tool");
        entry.put("name", event.getToolName());
        entry.put("label", event.getToolLabel());
        entry.put("ok", event.getToolOk());
        entry.put("summary", event.getToolSummary());
        return entry;
    }

    /** 草稿也要进轨迹：前端重放历史时要能把预约卡片重新渲染出来 */
    private Map<String, Object> draftTraceEntry(AiChatEventVO event) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("kind", "draft");
        entry.put("draft", event.getDraft());
        return entry;
    }

    private void persistAsync(Long userId, String sessionId, SignalType signal, String answer,
                              List<String> toolTrace, AtomicReference<Integer> latency,
                              AtomicReference<Integer> inTokens, AtomicReference<Integer> outTokens) {
        boolean hasAnswer = StringUtils.hasText(answer);
        String traceJson = toolTrace.isEmpty() ? null : "[" + String.join(",", toolTrace) + "]";
        String signalName = signal.name();
        dbExecutor.execute(() -> {
            try {
                if (hasAnswer) {
                    aiConversationService.appendAssistantMessage(userId, sessionId, answer, traceJson,
                            inTokens.get(), outTokens.get(), latency.get());
                } else {
                    log.info("本次回答为空不落库 session={} signal={}", sessionId, signalName);
                }
            } catch (Exception e) {
                // 落库失败不能让用户已经看到的回答变成错误，只记日志
                log.error("AI 消息落库失败 session={}", sessionId, e);
            }
        });
    }

    private AiChatEventVO parse(String json) {
        try {
            return objectMapper.readValue(json, AiChatEventVO.class);
        } catch (Exception e) {
            log.warn("无法解析 AI 事件帧，已跳过: {}", abbreviate(json), e);
            return null;
        }
    }

    /**
     * 序列化成一帧 SSE。
     * <p>
     * 实测确认（curl 观察原始字节）：Spring MVC 对 Flux&lt;String&gt; + produces=text/event-stream
     * 走 ReactiveTypeHandler，会把每个元素按 SSE 规范包装——自动加 {@code data:} 前缀、
     * 元素内部的换行也逐行加前缀，最后补空行。所以这里**只返回裸 JSON**，
     * 自己再拼 "data: …\n\n" 会被二次包装成 "data:data: …"。
     */
    private String frame(AiChatEventVO event) {
        return toJson(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("AI 事件序列化失败", e);
            return "{\"type\":\"error\",\"message\":\"服务内部错误\"}";
        }
    }

    private AiChatEventVO startEvent(String sessionId) {
        AiChatEventVO event = new AiChatEventVO();
        event.setType("start");
        event.setSessionId(sessionId);
        return event;
    }

    private AiChatEventVO errorEvent(String message) {
        AiChatEventVO event = new AiChatEventVO();
        event.setType("error");
        event.setMessage(message);
        return event;
    }

    /** 上游异常转成用户能看懂的话。BusinessException 的 message 已经是面向用户的 */
    private String friendlyMessage(Throwable e) {
        String message = e.getMessage();
        return StringUtils.hasText(message) ? message : "AI 助手暂时不可用，请稍后再试";
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
