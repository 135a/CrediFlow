package com.crediflow.user.realname.util;

import com.crediflow.common.kyc.IdCardFingerprintCalculator;

public final class IdCardFingerprint {

    private IdCardFingerprint() {}

    public static String hmacHex(String salt, String realName, String idCardNo) {
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("idempotencySalt 不能为空");
        }
        return IdCardFingerprintCalculator.hmacSha256Hex(salt, realName, idCardNo);
    }
}
