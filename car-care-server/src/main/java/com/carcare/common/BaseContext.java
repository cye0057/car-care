package com.carcare.common;

/**
 * 登录用户上下文：LoginInterceptor 解析 token 成功后写入当前用户信息，
 * 同一请求线程内 Service 层随处可取（如获取操作人 id），
 * 请求结束后由拦截器 afterCompletion 清理，防止线程池复用导致串数据
 */
public class BaseContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE = new ThreadLocal<>();

    public static void setCurrentUser(Long userId, Integer role) {
        USER_ID.set(userId);
        ROLE.set(role);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Integer getRole() {
        return ROLE.get();
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }
}
