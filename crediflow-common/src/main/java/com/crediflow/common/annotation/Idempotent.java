package com.crediflow.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /**
     * SpEL表达式，用于提取幂等键 (例如 "#idmpToken")
     */
    String key() default "";
    
    /**
     * 获取锁等待时间，默认 0 秒（快速失败）
     */
    long waitTime() default 0;

    /**
     * 锁自动释放时间，默认 10 秒
     */
    long leaseTime() default 10;
}
