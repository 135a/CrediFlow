package com.crediflow.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 忽略认证注解，加了此注解的接口完全公开（如登录、注册）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreAuth {
}
