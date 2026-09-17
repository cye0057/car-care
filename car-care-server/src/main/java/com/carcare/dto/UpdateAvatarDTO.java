package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新头像请求：avatar 为图片上传接口返回的完整 URL
 */
@Data
@Schema(description = "更新头像请求")
public class UpdateAvatarDTO {

    @NotBlank(message = "头像地址不能为空")
    @Schema(description = "头像 URL（上传接口返回）")
    private String avatar;
}
