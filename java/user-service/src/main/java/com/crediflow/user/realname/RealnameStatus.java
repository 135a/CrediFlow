package com.crediflow.user.realname;

/**
 * 与 {@code cf_user_kyc.realname_status} 字符串取值一致。
 */
public enum RealnameStatus {
    NOT_SUBMITTED,
    PROCESSING,
    VERIFIED,
    FAILED;

    public static RealnameStatus fromDb(String v) {
        if (v == null || v.isBlank()) {
            return NOT_SUBMITTED;
        }
        try {
            return RealnameStatus.valueOf(v);
        } catch (IllegalArgumentException e) {
            return NOT_SUBMITTED;
        }
    }
}
