package com.crediflow.user.realname.model;

/**
 * @param retryable  true 时不应写入实名失败终态
 * @param terminalFailure 明确业务失败（不一致/无效证件）
 */
public record RealnameVerifyResult(
        boolean matched,
        boolean idCardValid,
        String providerTxnNo,
        String internalReasonCode,
        String userMessageSummary,
        boolean retryable,
        boolean terminalFailure) {

    public static RealnameVerifyResult retryLater(String code, String message) {
        return new RealnameVerifyResult(false, false, null, code, message, true, false);
    }

    public static RealnameVerifyResult terminal(boolean matched, boolean idValid, String txn, String code, String msg) {
        return new RealnameVerifyResult(matched, idValid, txn, code, msg, false, true);
    }

    public static RealnameVerifyResult success(String txn) {
        return new RealnameVerifyResult(true, true, txn, "OK", "核验通过", false, false);
    }

    public boolean effectiveSuccess() {
        return matched && idCardValid && !retryable && !terminalFailure;
    }
}
