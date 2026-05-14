package com.crediflow.common.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
/**
 * 内部认证拦截器类
 * 用于在请求中添加内部认证的token头信息
 */
public class InternalAuthInterceptor implements RequestInterceptor {

    /**
     * 内部认证请求头的名称常量
     * 定义了用于内部认证的HTTP头名称
     */
    public static final String INTERNAL_AUTH_HEADER = "X-Internal-Token";

    private final JwtUtils jwtUtils;

    public InternalAuthInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    /**
     * 实现RequestInterceptor接口的apply方法
     * 在每个请求被发送前，会自动添加内部认证token到请求头中
     *
     * @param template 请求模板对象，用于修改即将发送的请求
     */
    @Override
    public void apply(RequestTemplate template) {
        // 调用JwtUtils生成内部token，并将其添加到请求头中
        template.header(INTERNAL_AUTH_HEADER, jwtUtils.generateInternalToken());
    }
}
