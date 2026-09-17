package com.carcare.controller;

import com.carcare.common.Result;
import com.carcare.dto.UpdateAvatarDTO;
import com.carcare.entity.User;
import com.carcare.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个人中心：当前用户信息查询与头像修改。
 * 路径在 /api/** 登录拦截范围内，token 即身份
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "02-用户中心", description = "个人信息/头像")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "当前用户信息", description = "含头像 URL，密码不返回")
    public Result<User> me() {
        return Result.success(userService.currentUser());
    }

    @PutMapping("/me")
    @Operation(summary = "更新头像", description = "body: {\"avatar\": \"OSS URL\"}")
    public Result<User> updateAvatar(@RequestBody @Valid UpdateAvatarDTO dto) {
        return Result.success(userService.updateAvatar(dto.getAvatar()));
    }
}
