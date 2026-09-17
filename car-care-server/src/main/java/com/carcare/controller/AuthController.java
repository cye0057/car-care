package com.carcare.controller;

import com.carcare.common.Result;
import com.carcare.dto.LoginDTO;
import com.carcare.dto.RegisterDTO;
import com.carcare.service.UserService;
import com.carcare.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：唯一免登录的 API 入口，登录成功后凭 token 访问其他接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "01-认证", description = "登录")
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "账号密码登录", description = "管理员与车主共用；返回 JWT，放入请求头 token 字段")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @PostMapping("/register")
    @Operation(summary = "车主注册", description = "账号唯一校验 + MD5 存密码，固定车主角色(role=1)，注册成功后跳转登录")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }
}
