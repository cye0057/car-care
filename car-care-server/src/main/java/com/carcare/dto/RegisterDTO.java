package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 车主注册请求：注册成功后固定 role=1（车主），需跳转登录
 */
@Data
@Schema(description = "注册请求")
public class RegisterDTO {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "账号需为 4-20 位字母、数字或下划线")
    @Schema(description = "登录账号（唯一）", example = "wangwu")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^\\S{6,20}$", message = "密码需为 6-20 位非空白字符")
    @Schema(description = "密码（明文传输，服务端 MD5 存储）", example = "123456")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Schema(description = "姓名/昵称", example = "王五")
    private String name;

    @Schema(description = "手机号（选填）", example = "13900000003")
    private String phone;
}
