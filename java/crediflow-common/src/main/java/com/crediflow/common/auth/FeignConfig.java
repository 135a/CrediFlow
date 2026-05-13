package com.crediflow.common.auth;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignConfig {

    @Bean
    public RequestInterceptor internalAuthInterceptor() {
        return new InternalAuthInterceptor();
    }

    @Bean
    public RequestInterceptor internalAuthRequestInterceptor() {
        return new com.crediflow.common.interceptor.InternalAuthRequestInterceptor();
    }

    @Bean
    public RequestInterceptor feignTraceInterceptor() {
        return new com.crediflow.common.trace.FeignTraceInterceptor();
    }
}
