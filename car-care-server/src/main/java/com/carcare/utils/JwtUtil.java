package com.carcare.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：HMAC-SHA256 签名。
 * token 载荷约定：subject=用户id，claims 含 role（角色）与 name（姓名）。
 * 密钥与有效期从配置文件 carcare.jwt.* 注入
 */
@Component
public class JwtUtil {

    @Value("${carcare.jwt.secret}")
    private String secret;

    @Value("${carcare.jwt.expire-minutes}")
    private long expireMinutes;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成登录令牌
     *
     * @param userId 用户 id，写入 subject
     * @param role   角色（0管理员 1车主），写入自定义 claim
     * @param name   用户姓名，写入自定义 claim
     * @return 签名后的 JWT 字符串
     */
    public String createToken(Long userId, Integer role, String name) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireMinutes * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("name", name)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    /**
     * 校验并解析 token（签名不合法 / 已过期均视为无效）
     *
     * @return 解析出的载荷；无效 token 返回 null，由调用方决定如何响应
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
