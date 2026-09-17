package com.carcare.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.carcare.common.BusinessException;
import com.carcare.config.AliOssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * 阿里云 OSS 上传封装：putObject 后返回公网可访问的图片 URL。
 * 访问规则 https://{bucket}.{endpoint}/{objectName}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssUploader {

    private final AliOssProperties props;

    public String upload(byte[] bytes, String objectName) {
        OSS client = new OSSClientBuilder()
                .build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
        try {
            client.putObject(props.getBucketName(), objectName, new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.error("OSS 上传失败, objectName={}", objectName, e);
            throw new BusinessException("图片上传失败，请稍后重试");
        } finally {
            client.shutdown();
        }
        return "https://" + props.getBucketName() + "." + props.getEndpoint() + "/" + objectName;
    }
}
