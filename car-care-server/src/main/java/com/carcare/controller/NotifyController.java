package com.carcare.controller;

import com.carcare.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝异步回调入口：不走统一 Result 包装，必须返回纯文本 success/failure，
 * 否则支付宝会按失败重试通知（路径在 WebMvcConfig 中放行登录拦截）
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
@Tag(name = "98-支付回调", description = "支付宝异步通知")
public class NotifyController {

    private final PayService payService;

    @PostMapping("/alipay")
    @Operation(summary = "支付宝支付结果异步通知", hidden = true)
    public String alipayNotify(HttpServletRequest request) {
        return payService.handleNotify(request);
    }
}
