package com.carcare.config;

import com.carcare.common.BaseContext;
import com.carcare.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器（无状态 JWT 方案）：
 * 1) 从请求头取 token → 验签与过期校验 → 401 拒绝
 * 2) /api/admin/** 额外要求管理员角色 → 403 拒绝
 * 3) 校验通过后把用户身份写入 BaseContext，供 Service 层使用
 * 后续演进：接入 Redis 后增加 token 黑名单，实现主动登出/踢人下线
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("token");
        if (!StringUtils.hasText(token)) {
            writeJson(response, 401, "{\"code\":401,\"msg\":\"未登录或登录已过期\"}");
            return false;
        }
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            writeJson(response, 401, "{\"code\":401,\"msg\":\"登录状态已过期，请重新登录\"}");
            return false;
        }
        Long userId = Long.valueOf(claims.getSubject());
        Integer role = claims.get("role", Integer.class);
        // 管理端接口要求管理员角色
        if (request.getRequestURI().startsWith("/api/admin") && (role == null || role != 0)) {
            writeJson(response, 403, "{\"code\":403,\"msg\":\"无权限访问\"}");
            return false;
        }
        BaseContext.setCurrentUser(userId, role);
        return true;
    }

    /** 请求结束必须清理 ThreadLocal，防止 Tomcat 线程复用造成用户身份泄漏 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.clear();
    }

    /** 拦截失败时直接写 JSON 响应体（不经过 ControllerAdvice） */
    private void writeJson(HttpServletResponse response, int status, String body) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(body);
    }
}
