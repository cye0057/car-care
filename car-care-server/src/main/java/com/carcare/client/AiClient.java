package com.carcare.client;

import com.carcare.common.BusinessException;
import com.carcare.dto.AiUpstreamChatDTO;
import com.carcare.vo.AiHealthVO;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * car-care-ai（Python FastAPI）的调用客户端。
 * <p>
 * 上游返回的是 SSE，这里用 WebClient 的 SSE 解码能力把它变成 Flux&lt;String&gt;
 * （每个元素是一帧 data 里的 JSON），再由 AiChatService 累积副作用后转发给前端。
 * <p>
 * 用 Flux 而不是自己开线程读 InputStream 的收益：
 * 1) 客户端断连时 Reactor 自动取消订阅 → WebClient 中止上游请求 →
 *    Python 侧收到 CancelledError → 停止模型生成。整条链路自动传导，不用手写取消逻辑；
 * 2) 背压由 Reactor 处理，不需要自己维护线程池与队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final WebClient aiWebClient;

    /** 健康检查自身的超时：探活要快，不能跟着 180 秒的对话超时一起等 */
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 发起流式对话。返回的 Flux 每发出一个元素就是一帧 SSE 数据（JSON 字符串）。
     * 底层异常统一转成 BusinessException，交给调用方转成 error 事件推给前端。
     */
    public Flux<String> chatStream(AiUpstreamChatDTO payload) {
        return aiWebClient.post()
                .uri("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            log.warn("AI 服务返回异常状态 {} body={}", response.statusCode(), abbreviate(body));
                            return Mono.error(new BusinessException(
                                    "AI 助手服务返回异常（" + response.statusCode().value() + "）"));
                        }))
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .map(ServerSentEvent::data)
                .filter(StringUtils::hasText)
                .onErrorMap(WebClientRequestException.class,
                        e -> new BusinessException("AI 助手服务未启动或无法连接，请联系管理员"))
                .onErrorMap(ReadTimeoutException.class,
                        e -> new BusinessException("AI 助手响应超时，请把问题拆小一点再问一次"));
    }

    /** 探活：任何异常都吞掉并返回 unavailable，因为 AI 服务不可用不该影响主流程 */
    public Mono<AiHealthVO> health() {
        return aiWebClient.get()
                .uri("/healthz")
                .retrieve()
                .bodyToMono(AiHealthVO.class)
                .timeout(HEALTH_TIMEOUT)
                .map(vo -> {
                    vo.setAvailable(true);
                    return vo;
                })
                .onErrorResume(e -> {
                    log.warn("AI 服务健康检查失败: {}", e.toString());
                    return Mono.just(AiHealthVO.unavailable("AI 助手服务未启动或无法连接"));
                });
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
