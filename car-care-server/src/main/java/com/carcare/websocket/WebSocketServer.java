package com.carcare.websocket;

import com.carcare.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 管理台 WebSocket 端点：/ws?token=JWT。
 * <p>
 * 握手时用 JWT 鉴权（复用登录体系的签名密钥，token 非法直接拒绝连接），
 * 会话按 userId 分组存储（同一用户多标签页），并记录角色供定向广播。
 * <p>
 * 集群方案：业务节点不直接推消息，而是发布到 Redis 频道 ws:notify，
 * 每个节点订阅后只投递给「连在自己身上」的会话——
 * 管理员的 ws 连接在哪个节点，消息就能到哪个节点，无需会话粘滞。
 * <p>
 * 注意：@ServerEndpoint 实例由容器每连接 new 一个，不是 Spring Bean，
 * 依赖（JwtUtil）通过 WebSocketConfig 静态注入。
 */
@Slf4j
@Component
@ServerEndpoint("/ws")
public class WebSocketServer {

    /** userId -> 该用户的全部会话（多标签页） */
    private static final Map<Long, Set<Session>> USER_SESSIONS = new ConcurrentHashMap<>();
    /** 管理员角色值 */
    private static final Integer ROLE_ADMIN = 0;

    /** 静态注入（@ServerEndpoint 非 Spring 管理），由 WebSocketConfig 赋值 */
    public static JwtUtil jwtUtil;

    @OnOpen
    public void onOpen(Session session) {
        String token = firstParam(session.getRequestParameterMap().get("token"));
        if (token == null || jwtUtil == null) {
            closeQuietly(session, 4001);
            return;
        }
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            closeQuietly(session, 4001); // 鉴权失败：policy violation 关闭
            return;
        }
        Long userId = Long.valueOf(claims.getSubject());
        Integer role = claims.get("role", Integer.class);
        session.getUserProperties().put("userId", userId);
        session.getUserProperties().put("role", role);
        USER_SESSIONS.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("WS 连接建立 userId={} role={} 在线用户数={}", userId, role, USER_SESSIONS.size());
    }

    @OnClose
    public void onClose(Session session) {
        Long userId = (Long) session.getUserProperties().get("userId");
        if (userId != null) {
            Set<Session> sessions = USER_SESSIONS.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    USER_SESSIONS.remove(userId);
                }
            }
        }
        log.info("WS 连接关闭 userId={} 在线用户数={}", userId, USER_SESSIONS.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.warn("WS 异常关闭：{}", error.getMessage());
        closeQuietly(session, 1011);
    }

    /** 心跳：前端定时发 ping，回 pong 维持连接与代理层超时 */
    @OnMessage
    public void onMessage(String message, Session session) {
        if ("ping".equals(message)) {
            try {
                session.getBasicRemote().sendText("pong");
            } catch (IOException ignored) {
            }
        }
    }

    /** 本节点广播给所有在线管理员（Redis 订阅回调入口） */
    public static void broadcastToAdmins(String payload) {
        USER_SESSIONS.forEach((userId, sessions) -> {
            for (Session s : sessions) {
                Integer role = (Integer) s.getUserProperties().get("role");
                if (ROLE_ADMIN.equals(role) && s.isOpen()) {
                    try {
                        s.getBasicRemote().sendText(payload);
                    } catch (IOException e) {
                        log.warn("WS 推送失败 userId={}", userId, e);
                    }
                }
            }
        });
    }

    public static int onlineCount() {
        return USER_SESSIONS.size();
    }

    private static String firstParam(java.util.List<String> params) {
        return params == null || params.isEmpty() ? null : params.get(0);
    }

    private static void closeQuietly(Session session, int code) {
        try {
            session.close(new jakarta.websocket.CloseReason(
                    jakarta.websocket.CloseReason.CloseCodes.getCloseCode(code), "auth failed"));
        } catch (Exception ignore) {
        }
    }
}
