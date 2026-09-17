package com.carcare.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录成功返回：前端保存 token 后在后续请求头中携带
 */
@Data
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "JWT 令牌，放入请求头 token 字段")
    private String token;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "用户姓名/昵称")
    private String name;

    @Schema(description = "头像 URL（阿里云 OSS）")
    private String avatar;

    @Schema(description = "角色：0管理员 1车主")
    private Integer role;
}
