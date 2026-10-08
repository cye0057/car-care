package com.carcare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.client.AiClient;
import com.carcare.common.BaseContext;
import com.carcare.common.Result;
import com.carcare.dto.AiChatDTO;
import com.carcare.dto.AiFeedbackDTO;
import com.carcare.entity.AiConversation;
import com.carcare.entity.AiMessage;
import com.carcare.service.AiChatService;
import com.carcare.service.AiConversationService;
import com.carcare.vo.AiHealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * 车主端 AI 养车顾问接口。
 * <p>
 * 所有接口都在 /api/** 之下，因此自动受 LoginInterceptor 保护，用户身份从
 * BaseContext 取——这也是「Java 是唯一网关」的体现：前端只认 car-care 的 JWT，
 * 永远不直接接触 Python 服务。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "18-AI助手", description = "养车顾问对话、会话历史与效果反馈")
public class AiController {

    private final AiChatService aiChatService;
    private final AiConversationService aiConversationService;
    private final AiClient aiClient;

    /**
     * 流式对话。返回 text/event-stream，每帧形如 {@code data: {"type":"delta","content":"…"}}。
     * <p>
     * 两个响应头是流式的必要条件：
     * - Cache-Control: no-cache —— 防止中间层缓存住半截回答；
     * - X-Accel-Buffering: no —— 关掉 Nginx 的响应缓冲，否则 Nginx 会把 SSE 攒成一大块再发，
     *   打字机效果直接消失（本地直连时不生效，部署到 Nginx 后面才会暴露，所以现在就加上）。
     * <p>
     * produces 里显式带 charset=UTF-8 也是必需的：text/event-stream 不带 charset 时，
     * 按 HTTP 规范客户端会按 ISO-8859-1 解码，中文全变乱码（浏览器 fetch 默认按 UTF-8 处理
     * 所以前端看不出问题，但 Python/Postman 这类严格客户端就会踩坑）。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @Operation(summary = "流式对话", description = "SSE；sessionId 留空则新建会话，通过 start 事件回传")
    public Flux<String> chat(@RequestBody @Valid AiChatDTO dto, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return aiChatService.chat(dto);
    }

    /**
     * AI 服务可用性。始终返回 200，用 available 字段表达状态——
     * AI 服务不可用不应该让前端把这个接口当失败处理，更不该影响主链路。
     * <p>
     * 这里用 block 而不是返回 Mono：探活频率极低，且 AiClient 内部已给 3 秒硬超时，
     * 阻塞是有界的。让这一个接口保持和其它接口一样的 Result 形状，比为了「纯非阻塞」
     * 把一个 Mono 混进全命令式的 Controller 里更值得。
     */
    @GetMapping("/health")
    @Operation(summary = "AI 服务可用性", description = "前端可据此决定是否展示 AI 入口")
    public Result<AiHealthVO> health() {
        return Result.success(aiClient.health().block(Duration.ofSeconds(4)));
    }

    @GetMapping("/conversations")
    @Operation(summary = "我的会话列表")
    public Result<Page<AiConversation>> conversations(@RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(aiConversationService.pageByUser(BaseContext.getUserId(), pageNum, pageSize));
    }

    @GetMapping("/conversations/{sessionId}/messages")
    @Operation(summary = "会话消息", description = "toolTrace 为 JSON 字符串，前端按需解析回放工具调用过程")
    public Result<List<AiMessage>> messages(@PathVariable String sessionId) {
        return Result.success(aiConversationService.messages(BaseContext.getUserId(), sessionId));
    }

    @DeleteMapping("/conversations/{sessionId}")
    @Operation(summary = "删除会话", description = "连同该会话的消息一起删除")
    public Result<Void> deleteConversation(@PathVariable String sessionId) {
        aiConversationService.delete(BaseContext.getUserId(), sessionId);
        return Result.success();
    }

    @PostMapping("/messages/{id}/feedback")
    @Operation(summary = "回答反馈", description = "1 赞 / 2 踩；只能评价自己的 AI 回答")
    public Result<Void> feedback(@PathVariable Long id, @RequestBody @Valid AiFeedbackDTO dto) {
        aiConversationService.feedback(BaseContext.getUserId(), id, dto.getFeedback());
        return Result.success();
    }
}
