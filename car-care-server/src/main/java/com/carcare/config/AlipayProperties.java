package com.carcare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝沙箱支付配置：由 application.yml 的 carcare.pay.alipay 前缀绑定。
 * 沙箱网关 openapi-sandbox.dl.alipaydev.com，正式环境换 openapi.alipay.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "carcare.pay.alipay")
public class AlipayProperties {

    /** 网关地址（沙箱/正式） */
    private String gateway;
    /** 应用 AppID */
    private String appId;
    /** 应用私钥（RSA2，含 BEGIN 头） */
    private String privateKey;
    /** 支付宝公钥（不是应用公钥） */
    private String alipayPublicKey;
    /** 支付结果异步通知地址，需公网可达 */
    private String notifyUrl;
}
