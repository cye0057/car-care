package com.carcare.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口鉴权：/api/internal/** 只允许 AI 服务用共享令牌调用，不接受车主 JWT。
 * <p>
 * 这些接口能按 userId 查任意用户的数据，所以不能挂 LoginInterceptor（那是给前端用的），
 * 必须用另一把钥匙。同时：
 * 1) 令牌未配置时直接 500——宁可服务不可用，也不要「配置漏了 = 完全开放」；
 * 2) 用 MessageDigest.isEqual 做定长比较，避免按字符短路的时序侧信道。
 * <p>
 * 真正的第一道防线是网络层：AI 服务只绑 127.0.0.1，本机之外根本到不了这里。
 */
@Component
@RequiredArgsConstructor
public class InternalTokenInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Internal-Token";

    private final AiProperties aiProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expected = aiProperties.getInternalToken();
        if (!StringUtils.hasText(expected)) {
            writeJson(response, 500, "{\"code\":500,\"msg\":\"服务端未配置内部调用令牌\"}");
            return false;
        }
        String actual = request.getHeader(HEADER);
        if (actual == null || !MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            writeJson(response, 401, "{\"code\":401,\"msg\":\"内部令牌校验失败\"}");
            return false;
        }
        return true;
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(body);
    }
}
