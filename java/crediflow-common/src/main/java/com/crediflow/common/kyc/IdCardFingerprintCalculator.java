package com.crediflow.common.kyc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 身份证 + 姓名指纹（HMAC-SHA256 十六进制），与 {@code RealnameVerificationService} 使用的
 * {@code com.crediflow.user.realname.util.IdCardFingerprint} 算法一致：载荷为 {@code realName + "|" + idCardNo}。
 */
public final class IdCardFingerprintCalculator {

    private IdCardFingerprintCalculator() {}

    public static String hmacSha256Hex(String salt, String realName, String idCardNo) {
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("salt 不能为空");
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
