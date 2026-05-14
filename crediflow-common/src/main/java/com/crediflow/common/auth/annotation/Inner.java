package com.crediflow.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 内部接口注解，加了此注解的接口只能在微服务内部调用，不允许网关对外暴露
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Inner {
    /**
     * 是否仅允许内部调用，默认为 true
     */
    boolean value() default true;
}
