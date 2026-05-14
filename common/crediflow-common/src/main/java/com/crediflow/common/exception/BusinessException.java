package com.crediflow.common.exception;

/**
 * 自定义业务异常类，继承自RuntimeException
 * 用于处理业务逻辑中的异常情况
 */
public class BusinessException extends RuntimeException {
    private final int code; // 错误码

    /**
     * 构造方法1：通过错误码和自定义消息创建异常
     * @param code 错误码
     * @param message 异常消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法2：通过错误码枚举创建异常
     * @param errorCode 错误码枚举，包含错误码和错误信息
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
    
    /**
     * 构造方法3：通过错误码枚举和自定义消息创建异常
     * @param errorCode 错误码枚举，包含错误码
     * @param customMessage 自定义的异常消息
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
    }

    /**
     * 获取错误码
     * @return 错误码
     */
    public int getCode() { return code; }
}
