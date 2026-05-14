package com.crediflow.user.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

public class ExternalJwtUtils {
    // 实际生产应使用 RSA 或从 Nacos 下发秘钥
    private static final String EXTERNAL_SECRET = "CrediFlowExternalAuthSecretKey1234567890!";
    private static final Key KEY = Keys.hmacShaKeyFor(EXTERNAL_SECRET.getBytes());
    private static final long EXPIRATION_MS = 86400000; // 1天

    public static String generateToken(Long userId, String role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }
}
