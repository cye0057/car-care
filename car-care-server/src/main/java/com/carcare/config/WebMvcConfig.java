package com.carcare.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器 + 内部令牌拦截器 + 开发环境跨域。
 * 生产环境跨域由 Nginx 统一处理，这里仅为本地前后端分离调试放开
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final InternalTokenInterceptor internalTokenInterceptor;

    /**
     * 拦截所有 /api/** 业务接口；登录接口、支付回调与 Swagger 文档路径放行。
     * <p>
     * /api/internal/** 是「AI 服务专用」通道，走独立令牌而不是车主 JWT，
     * 所以要从登录拦截器里排除，交给 InternalTokenInterceptor 单独把关。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/notify/**",
                        "/api/internal/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );

        registry.addInterceptor(internalTokenInterceptor)
                .addPathPatterns("/api/internal/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
