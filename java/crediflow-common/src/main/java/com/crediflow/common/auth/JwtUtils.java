package com.crediflow.common.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT（JSON Web Token）工具类
 * 用于生成和验证内部服务调用使用的JWT令牌
 * 主要功能包括：
 * 1. 生成内部服务调用的JWT令牌
 * 2. 验证内部服务JWT令牌的有效性
 *
 * 密钥优先从 Nacos 配置中心读取（crediflow.auth.jwt-secret），
 * 如未配置则使用默认值
 */
@Component
public class JwtUtils {

    /** 内部调用 Token 有效期 1 分钟 */
    private static final long EXPIRATION_MS = 60000;

    /**
     * JWT 签名密钥，优先从 Nacos 配置读取，未配置则使用默认值
     */
    @Value("${crediflow.auth.jwt-secret:CrediFlowInternalAuthSecretKey1234567890!}")
    private String secret;

    /** 根据 secret 派生的签名 Key */
    private Key key;

    /**
     * Bean 初始化后根据 secret 生成签名 Key
     */
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * 生成内部服务调用使用的JWT令牌
     *
     * @return JWT令牌字符串
     * 令牌包含以下信息：
     * - 主题（Subject）: "internal-service"
     * - 签发时间: 当前时间
     * - 过期时间: 当前时间后1分钟
     * - 签名算法: HS256
     */
    public String generateInternalToken() {
        return Jwts.builder()
                .setSubject("internal-service")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 验证内部服务JWT令牌的有效性
     *
     * @param token 需要验证的JWT令牌字符串
     * @return 如果令牌有效返回true，无效返回false
     * 验证内容包括：
     * - 签名验证
     * - 过期时间检查
     * - 令牌格式验证
     */
    public boolean validateInternalToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
