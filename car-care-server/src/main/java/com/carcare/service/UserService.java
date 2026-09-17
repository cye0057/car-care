package com.carcare.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.common.BaseContext;
import com.carcare.common.BusinessException;
import com.carcare.dto.LoginDTO;
import com.carcare.dto.RegisterDTO;
import com.carcare.entity.User;
import com.carcare.mapper.UserMapper;
import com.carcare.utils.JwtUtil;
import com.carcare.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户/登录服务：账号密码校验 + JWT 签发。
 * 管理端与车主端共用同一入口，登录成功后按 role 决定可访问的接口域
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * 登录流程：查用户 → MD5 比对密码 → 校验启用状态 → 签发 token。
     * 注意：密码不匹配与用户不存在返回同一提示，避免账号枚举
     */
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, dto.getUsername()));
        if (user == null || !user.getPassword().equals(SecureUtil.md5(dto.getPassword()))) {
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.createToken(user.getId(), user.getRole(), user.getName()));
        vo.setUserId(user.getId());
        vo.setName(user.getName());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        return vo;
    }

    /** 车主注册：用户名唯一校验 → MD5 存密码 → 固定车主角色(role=1)。注册成功后需跳转登录 */
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("账号已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(SecureUtil.md5(dto.getPassword()));
        user.setName(dto.getName());
        user.setPhone(dto.getPhone() == null || dto.getPhone().isBlank() ? null : dto.getPhone());
        user.setRole(1);
        user.setStatus(1);
        userMapper.insert(user);
    }

    /** 当前登录用户信息（密码不返回） */
    public User currentUser() {
        Long userId = BaseContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    /** 更新头像：只改 avatar 字段，返回更新后的用户信息 */
    public User updateAvatar(String avatar) {
        Long userId = BaseContext.getUserId();
        User patch = new User();
        patch.setId(userId);
        patch.setAvatar(avatar);
        userMapper.updateById(patch);
        return currentUser();
    }
}
