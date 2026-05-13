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
    REALNAME_ID_CARD_INVALID(1006, "身份证号格式或校验位不正确");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
