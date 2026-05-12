package com.crediflow.common.exception;

import com.crediflow.common.trace.TraceIdContext;
import com.crediflow.common.web.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage(), TraceIdContext.getTraceId());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数校验异常: {}", msg);
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), msg, TraceIdContext.getTraceId());
    }
    
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        return Result.error(404, "接口不存在", TraceIdContext.getTraceId());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 禁止向前端返回堆栈
        log.error("系统异常:", e);
        return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage(), TraceIdContext.getTraceId());
    }
}
