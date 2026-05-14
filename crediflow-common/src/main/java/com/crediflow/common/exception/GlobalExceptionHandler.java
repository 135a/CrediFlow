package com.crediflow.common.exception;

import com.crediflow.common.trace.TraceIdContext;
import com.crediflow.common.web.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器
 * 用于统一处理系统中抛出的各种异常，并返回统一的响应格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     * @param e 业务异常对象
     * @return 统一的响应结果，包含错误码、错误信息和追踪ID
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage(), TraceIdContext.getTraceId());
    }

    /**
     * 处理参数校验异常
     * @param e 参数校验异常对象
     * @return 统一的响应结果，包含错误码、错误信息和追踪ID
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数校验异常: {}", msg);
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), msg, TraceIdContext.getTraceId());
    }
    
    /**
     * 处理接口不存在异常
     * @param e 接口不存在异常对象
     * @return 统一的响应结果，包含404错误码、错误信息和追踪ID
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        return Result.error(404, "接口不存在", TraceIdContext.getTraceId());
    }

    /**
     * 处理系统其他异常
     * @param e 系统异常对象
     * @return 统一的响应结果，包含系统错误码、错误信息和追踪ID
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 禁止向前端返回堆栈
        log.error("系统异常:", e);
        return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage(), TraceIdContext.getTraceId());
    }
}
