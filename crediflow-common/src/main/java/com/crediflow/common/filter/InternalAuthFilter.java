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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 内部认证过滤器，用于处理内部API请求的认证和授权
 * 通过检查请求头中的时间戳和签名来验证请求的合法性
 *
 * @author CodeGeeX
 * @version 1.0
 */
@Component
@Order(1)
public class InternalAuthFilter implements Filter {

    /**
     * 内部认证的密钥，用于生成和验证请求签名
     * 默认值为 "default-secret-key-123"
     */
    @Value("${crediflow.internal.secret:default-secret-key-123}")
    private String secretKey;

    /**
     * 不走平台 {@code X-Internal-Sign} 校验的 {@code /api/internal/**} 路径白名单（如人脸厂商异步回调），
     * 由调用方自身的签名层（Provider.verifySignature）+ 边缘 IP 白名单完成防护。
     */
    @Value("${crediflow.internal.public-paths:/api/internal/face-verify/callback}")
    private String publicPathsCsv;

    private List<String> publicPaths() {
        if (publicPathsCsv == null || publicPathsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(publicPathsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // 白名单路径（厂商异步回调等）由各自的签名层 + 边缘防护处理，不再要求 X-Internal-Sign
        for (String publicPath : publicPaths()) {
            if (path.equals(publicPath) || path.startsWith(publicPath + "/")) {
                chain.doFilter(request, response);
                return;
            }
        }

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
