package com.crediflow.common.web;

public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private String traceId;

    public Result() {}

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(Integer code, String message, String traceId) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.traceId = traceId;
        return result;
    }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
