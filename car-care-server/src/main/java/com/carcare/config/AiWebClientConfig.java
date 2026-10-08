package com.carcare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 服务客户端配置。
 * <p>
 * 这里只用到 WebFlux 的 WebClient，HTTP 服务端仍然是 Tomcat
 * （spring-boot-starter-web 存在时 Spring Boot 判定为 SERVLET 应用）。
 */
@Configuration
public class AiWebClientConfig {

    /**
     * 连接 AI 服务的 WebClient。
     * <p>
     * 两个超时的分工：
     * - connectTimeout：建连超时，AI 服务在本机，3 秒还没连上就是没起来，快速失败；
     * - responseTimeout：reactor-netty 的「两次网络读之间的最大间隔」，也就是**空闲超时**，
     *   而不是总时长上限。模型思考 60 秒才吐第一个字属于正常，所以这里给到 180 秒。
     * <p>
     * 总时长上限刻意不在这里设：Python 侧用 asyncio.timeout 对自己的单次请求做了硬预算
     * （默认 120s），一定会主动结束并推一个 error 事件。让上游自己认输，
     * 比下游先掐断、上游还在继续烧 token 要干净。
     */
    @Bean
    public WebClient aiWebClient(AiProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getStreamTimeoutMs()));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeaders(headers -> {
                    // 令牌没配时不塞空头，让 Python 侧明确报「令牌校验失败」而不是诡异的空值比较
                    if (StringUtils.hasText(properties.getInternalToken())) {
                        headers.set("X-Internal-Token", properties.getInternalToken());
                    }
                })
                .codecs(configurer -> {
                    // 流式响应是逐帧到达的，缓冲区给足；单帧不会大，4MB 是安全余量
                    configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024);
                    // 关键：WebClient.builder() 默认用「裸」的 Jackson 编码器，不读 Spring 的
                    // ObjectMapper 配置（日期会变成 [2026,9,15] 数组而不是字符串）。
                    // 这里显式换成容器里那个，保证出网 JSON 与接口契约一致。
                    configurer.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    configurer.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                })
                .build();
    }

    /**
     * 落库专用线程池。
     * <p>
     * 对话结束后要把消息写进 MySQL，而 JDBC 是阻塞的——直接在 Reactor 事件循环上做
     * 会拖慢整个事件循环（Reactor 的线程数等于 CPU 核数，阻塞一个就少一个）。
     * 所以把写库副作用丢到这个有界池上，并且队列满了就由调用线程自己跑（CallerRunsPolicy），
     * 天然形成背压，不会无限堆积把内存吃满。
     */
    @Bean(name = "aiDbExecutor", destroyMethod = "shutdown")
    public ExecutorService aiDbExecutor() {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(
                2, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "ai-db-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
