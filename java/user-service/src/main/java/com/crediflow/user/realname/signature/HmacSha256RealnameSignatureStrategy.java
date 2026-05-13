package com.crediflow.user.realname.signature;

import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component("hmacSha256RealnameSignatureStrategy")
public class HmacSha256RealnameSignatureStrategy implements RealnameSignatureStrategy {

    private final RealnameProperties properties;

    public HmacSha256RealnameSignatureStrategy(RealnameProperties properties) {
        this.properties = properties;
    }

    @Override
    public String sign(String bodyWithPlaceholdersResolvedExceptSignature, RealnameVerifyCommand command, long timestampMillis) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(
                    properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] out = mac.doFinal(bodyWithPlaceholdersResolvedExceptSignature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }
}
