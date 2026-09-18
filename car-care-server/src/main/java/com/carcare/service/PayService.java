package com.carcare.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.carcare.common.BaseContext;
import com.carcare.common.BusinessException;
import com.carcare.config.AlipayProperties;
import com.carcare.entity.Order;
import com.carcare.mapper.OrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付宝沙箱支付：手机网站支付（wap）下单 + 异步回调验签。
 * 下单返回可跳转的收银台 URL；回调经 rsaCheckV1 验签后驱动订单状态机 1→2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private final AlipayProperties props;
    private final OrderMapper orderMapper;
    private final OrderService orderService;

    /**
     * 发起支付下单：校验订单归属与待支付状态，返回支付宝收银台跳转 URL
     */
    public String createPayUrl(Long orderId) {
        Long userId = BaseContext.getUserId();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("当前订单状态不可支付");
        }
        AlipayClient client = new DefaultAlipayClient(
                props.getGateway(), props.getAppId(), props.getPrivateKey(),
                "json", "UTF-8", props.getAlipayPublicKey(), "RSA2");
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(props.getNotifyUrl());
        request.setBizContent("{"
                + "\"out_trade_no\":\"" + order.getOrderNo() + "\","
                + "\"total_amount\":\"" + order.getActualAmount().toPlainString() + "\","
                + "\"subject\":\"养车服务订单 " + order.getOrderNo() + "\","
                + "\"product_code\":\"QUICK_WAP_WAY\""
                + "}");
        try {
            // GET 模式下 body 为可直接跳转的完整网关 URL
            AlipayTradeWapPayResponse response = client.pageExecute(request, "GET");
            if (!response.isSuccess()) {
                log.warn("支付宝下单失败 orderNo={}, code={}, msg={}, subMsg={}",
                        order.getOrderNo(), response.getCode(), response.getMsg(), response.getSubMsg());
                throw new BusinessException("支付下单失败：" + response.getSubMsg());
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            log.error("支付宝下单异常 orderNo={}", order.getOrderNo(), e);
            throw new BusinessException("支付下单失败，请稍后重试");
        }
    }

    /**
     * 支付宝异步回调：验签通过且支付成功则幂等置订单为已支付。
     * 返回纯文本 "success"/"failure"，success 后支付宝不再重试。
     * <p>
     * 日志分级：缺 sign / 验签不通过属于「请求本身不合法」，是预期内的拒绝
     * （扫描器探测、手工 curl 都会命中），只打一行 WARN，不输出堆栈；
     * 只有验签通过后的业务异常才按 ERROR 记录，保证真实故障不被噪声淹没
     */
    public String handleNotify(HttpServletRequest request) {
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));
        if (!StringUtils.hasText(params.get("sign"))) {
            log.warn("支付宝回调缺少签名，已拒绝 out_trade_no={}, 参数={}", params.get("out_trade_no"), params.keySet());
            return "failure";
        }
        try {
            boolean valid = AlipaySignature.rsaCheckV1(params, props.getAlipayPublicKey(), "UTF-8", "RSA2");
            if (!valid) {
                log.warn("支付宝回调验签不通过 out_trade_no={}", params.get("out_trade_no"));
                return "failure";
            }
            String tradeStatus = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                orderService.paySuccessByOrderNo(params.get("out_trade_no"));
                log.info("支付宝支付成功 out_trade_no={}, trade_no={}", params.get("out_trade_no"), params.get("trade_no"));
            }
            return "success";
        } catch (AlipayApiException e) {
            // 验签阶段的 SDK 异常同样是「请求不合法」，堆栈降级到 debug 供排查
            log.warn("支付宝回调验签异常：{}", e.getMessage());
            log.debug("验签异常堆栈", e);
            return "failure";
        } catch (BusinessException e) {
            log.warn("支付宝回调业务处理失败：{}", e.getMessage());
            return "failure";
        }
    }
}
