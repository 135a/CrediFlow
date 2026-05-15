package com.crediflow.common.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.crediflow.common.util.HmacUtils;

public class InternalAuthRequestInterceptor implements RequestInterceptor {

    @Value("${crediflow.internal.secret:default-secret-key-123}")
    private String secretKey;

    @Override
    public void apply(RequestTemplate template) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String dataToSign = template.url() + timestamp;
        String signature = generateHmacSHA256(dataToSign, secretKey);
        
        template.header("X-Timestamp", timestamp);
        template.header("X-Internal-Sign", signature);
    }

    private String generateHmacSHA256(String data, String key) {
        try {
            return HmacUtils.generateHmacSHA256(data, key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate internal signature", e);
        }
    }
}
