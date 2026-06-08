package com.archive.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类.
 *
 * 使用 HS256 对称加密,密钥从配置读取(实际项目应该用更复杂的密钥管理).
 * Token 默认 8 小时过期.
 *
 * @author Mavis
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret:change-me-please-this-is-a-default-secret-key-32bytes}")
    private String secret;

    @Value("${app.jwt.expiration-seconds:28800}")  // 8 小时
    private long expirationSeconds;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // HS256 至少需要 256 bit (32 字节) 密钥
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            log.warn("JWT secret 长度小于 32 字节,自动 padding. 生产环境请用更长的随机密钥.");
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Token.
     */
    public String generate(Long userId, String username, String roleCode) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("username", username);
        claims.put("role", roleCode);

        long now = System.currentTimeMillis();
        Date expiry = new Date(now + expirationSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 Token,失败返回 null.
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
