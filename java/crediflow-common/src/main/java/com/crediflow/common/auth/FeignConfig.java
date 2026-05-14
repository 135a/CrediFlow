package com.crediflow.common.auth;

import com.crediflow.common.interceptor.InternalAuthRequestInterceptor;
import com.crediflow.common.trace.FeignTraceInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign配置类
 * 当类路径中存在RequestInterceptor类时，才会创建该配置类
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignConfig {

    /**
     * 创建内部认证拦截器
     * @return 返回InternalAuthInterceptor实例
     */
    @Bean
    public RequestInterceptor internalAuthInterceptor() {
        return new InternalAuthInterceptor();
    }

    /**
     * 创建内部认证请求拦截器
     * @return 返回InternalAuthRequestInterceptor实例
     */
    @Bean
    public RequestInterceptor internalAuthRequestInterceptor() {
        return new InternalAuthRequestInterceptor();
    }

    /**
     * 创建Feign跟踪拦截器
     * @return 返回FeignTraceInterceptor实例
     */
    @Bean
    public RequestInterceptor feignTraceInterceptor() {
        return new FeignTraceInterceptor();
    }
}
