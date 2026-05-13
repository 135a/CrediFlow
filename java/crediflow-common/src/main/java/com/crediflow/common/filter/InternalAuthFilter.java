package com.crediflow.common.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Order(1)
public class InternalAuthFilter implements Filter {

    @Value("${crediflow.internal.secret:default-secret-key-123}")
    private String secretKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
            
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        
        // 仅拦截 /api/internal/** 路径
        if (path.startsWith("/api/internal/")) {
            String timestamp = httpRequest.getHeader("X-Timestamp");
            String signature = httpRequest.getHeader("X-Internal-Sign");

            if (timestamp == null || signature == null) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Internal Auth Headers");
                return;
            }

            // 防重放：检查时间戳是否超过 5 分钟
            long requestTime = Long.parseLong(timestamp);
            if (System.currentTimeMillis() - requestTime > 5 * 60 * 1000) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Request Expired");
                return;
            }

            // 验签
            String dataToSign = path + timestamp; // 这里简化的签名逻辑，应和发出时保持一致
            String expectedSignature = generateHmacSHA256(dataToSign, secretKey);

            if (!expectedSignature.equals(signature)) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Internal Signature");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String generateHmacSHA256(String data, String key) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate internal signature", e);
        }
    }
}
