package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起一次 AI 对话。sessionId 留空表示新建会话，由服务端生成后通过 start 事件回传
 */
@Data
@Schema(description = "AI 对话请求")
public class AiChatDTO {

    @Schema(description = "会话 id；留空则新建会话")
    private String sessionId;

    @NotBlank(message = "请输入你想问的内容")
    @Size(max = 2000, message = "单次提问不能超过 2000 字")
    @Schema(description = "用户问题", example = "我的车该保养了吗")
    private String message;
}
