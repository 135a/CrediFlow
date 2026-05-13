package com.crediflow.common.exception;

public enum ErrorCode {
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统内部错误"),
    UNAUTHORIZED(401, "未授权或Token失效"),
    FORBIDDEN(403, "没有权限访问该资源"),
    PARAM_ERROR(400, "参数错误"),
    BUSINESS_ERROR(1000, "业务逻辑异常"),
    REALNAME_RATE_LIMIT(1001, "实名核验过于频繁，请稍后再试"),
    REALNAME_RETRY_LATER(1002, "实名核验通道繁忙，请稍后重试"),
    REALNAME_VERIFY_FAILED(1003, "实名核验未通过"),
    REALNAME_CONFIG_ERROR(1004, "实名服务配置错误"),
    REALNAME_NOT_VERIFIED(1005, "请先完成实名核验"),
    REALNAME_ID_CARD_INVALID(1006, "身份证号格式或校验位不正确"),
    KYC_AGE_NOT_ELIGIBLE(1101, "您的年龄不符合开户要求"),
    KYC_ID_CARD_DUPLICATED(1102, "该身份证已绑定其他账号"),
    KYC_BLOCKED_BY_RISK(1103, "风险拦截，无法继续操作"),
    KYC_ELIGIBILITY_RATE_LIMIT(1104, "操作过于频繁，请稍后再试"),
    KYC_RISK_UPSTREAM_UNAVAILABLE(1105, "风控通道暂不可用，请稍后再试"),
    KYC_FACE_NOT_VERIFIED(1106, "请先完成 KYC 实名实人核验"),
    KYC_FACE_RETRYABLE(1107, "人脸核验通道繁忙，请稍后重试"),
    KYC_FACE_VERIFY_FAILED(1108, "人脸核验未通过"),
    KYC_BANKCARD_VERIFY_FAILED(1110, "银行卡四要素校验未通过"),
    KYC_BANKCARD_DUPLICATED(1111, "该银行卡已绑定"),
    KYC_BANKCARD_REQUIRED(1112, "请先完成银行卡四要素绑卡"),
    KYC_LEGACY_API_GONE(1199, "请使用 KYC v2 流程");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
