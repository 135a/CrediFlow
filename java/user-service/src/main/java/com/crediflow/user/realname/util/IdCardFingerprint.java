package com.crediflow.user.realname.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class IdCardFingerprint {

    private IdCardFingerprint() {}

    public static String hmacHex(String salt, String realName, String idCardNo) {
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("idempotencySalt 不能为空");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = (realName == null ? "" : realName) + "|" + (idCardNo == null ? "" : idCardNo);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("指纹计算失败", e);
        }
    }
}
