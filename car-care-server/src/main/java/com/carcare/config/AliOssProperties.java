package com.carcare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置：endpoint 形如 oss-cn-beijing.aliyuncs.com，
 * 由 application.yml 的 carcare.alioss 前缀绑定
 */
@Data
@Component
@ConfigurationProperties(prefix = "carcare.alioss")
public class AliOssProperties {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
}
