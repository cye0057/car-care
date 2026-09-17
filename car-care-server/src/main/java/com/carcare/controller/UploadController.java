package com.carcare.controller;

import com.carcare.common.BusinessException;
import com.carcare.common.Result;
import com.carcare.utils.OssUploader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 通用图片上传（阿里云 OSS），管理端与用户端共用：
 * 目录按日期分片 car-care/yyyy/MM/，文件名 UUID 避免重名覆盖。
 * 受 /api/** 登录拦截保护，任意登录角色均可使用
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Tag(name = "99-通用上传", description = "图片上传（阿里云 OSS）")
public class UploadController {

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final OssUploader ossUploader;

    @PostMapping
    @Operation(summary = "上传图片", description = "multipart 表单字段名 file；返回可访问的完整 URL")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("图片大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("仅支持上传图片文件");
        }
        String objectName = "car-care/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + suffixOf(file.getOriginalFilename());
        try {
            return Result.success(ossUploader.upload(file.getBytes(), objectName));
        } catch (IOException e) {
            log.error("读取上传文件流失败", e);
            throw new BusinessException("图片上传失败，请稍后重试");
        }
    }

    private String suffixOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
