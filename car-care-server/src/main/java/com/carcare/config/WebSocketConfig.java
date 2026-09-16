package com.carcare.config;

import com.carcare.utils.JwtUtil;
import com.carcare.websocket.WebSocketServer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 装配：
 * 1) ServerEndpointExporter 注册 @ServerEndpoint 端点（内嵌 Tomcat 必需）；
 * 2) 把 Spring 管理的 JwtUtil 静态注入给非托管的端点实例；
 * 3) 订阅 Redis 频道 ws:notify——任意业务节点发布的通知，
 *    由持有该管理员连接的节点投递，实现集群扇出（单机部署时等价于本地直发）。
 * <p>
 * @ConditionalOnWebApplication：ServerEndpointExporter 依赖 Servlet 容器的
 * ServerContainer，纯上下文测试（webEnvironment=NONE）下跳过装配。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSocketConfig implements InitializingBean {

    public static final String WS_NOTIFY_CHANNEL = "ws:notify";

    private final JwtUtil jwtUtil;

    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterPropertiesSet() {
        WebSocketServer.jwtUtil = this.jwtUtil;
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public RedisMessageListenerContainer wsNotifyListener(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(
                // 发布端 StringRedisSerializer 是 UTF-8；Windows JVM 默认字符集是 GBK，
                // 必须显式按 UTF-8 解码，否则中文文案乱码（实测踩坑）
                (message, pattern) -> WebSocketServer.broadcastToAdmins(
                        new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8)),
                new ChannelTopic(WS_NOTIFY_CHANNEL));
        return container;
    }
}
