package com.crediflow.common.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;

public class JwtUtils {
    // 内部互信固定密钥（实际应通过 Nacos 配置动态下发）
    private static final String SECRET = "CrediFlowInternalAuthSecretKey1234567890!";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes());
    private static final long EXPIRATION_MS = 60000; // 内部调用 Token 有效期 1 分钟

    public static String generateInternalToken() {
        return Jwts.builder()
                .setSubject("internal-service")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    public static boolean validateInternalToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
