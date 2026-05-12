package com.crediflow.common.exception;

public enum ErrorCode {
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统内部错误"),
    UNAUTHORIZED(401, "未授权或Token失效"),
    FORBIDDEN(403, "没有权限访问该资源"),
    PARAM_ERROR(400, "参数错误"),
    BUSINESS_ERROR(1000, "业务逻辑异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
