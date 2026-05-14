package com.crediflow.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 忽略认证注解，加了此注解的接口完全公开（如登录、注册）
 * 这是一个自定义注解，用于标记不需要进行身份认证的接口方法或类
 *
 * @author CodeGeeX
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})  // 注解可以用于方法和类上
@Retention(RetentionPolicy.RUNTIME)  // 注解会在运行时保留
@Documented  // 注解会被包含在JavaDoc中
public @interface IgnoreAuth {
    // 这是一个空注解，仅用作标记，不需要定义属性
    // 当接口或方法被此注解标记时，表示该接口不需要进行身份验证
    // 常用于登录、注册等公开接口
}
