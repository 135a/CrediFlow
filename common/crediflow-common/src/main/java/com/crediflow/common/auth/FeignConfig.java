package com.crediflow.common.auth;

import com.crediflow.common.interceptor.InternalAuthRequestInterceptor;
import com.crediflow.common.trace.FeignTraceInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 全局拦截器配置。
 * <p>
 * 内网 {@code /api/internal/**} 调用方 MUST 通过 {@link InternalAuthRequestInterceptor}
 * 注入 {@code X-Timestamp} 与 {@code X-Internal-Sign}（HMAC），与
 * {@link com.crediflow.common.filter.InternalAuthFilter} 验签一致；此处不再注册
 * 已移除的 JWT 头注入，避免与规范主链路重复及误导。
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignConfig {

    /**
     * 内网 HMAC 签名拦截器（与 Java 接收端 {@code InternalAuthFilter}、Go {@code internalsign} 算法一致）。
     */
    @Bean
    public RequestInterceptor internalAuthRequestInterceptor() {
        return new InternalAuthRequestInterceptor();
    }

    /**
     * Feign 调用链路上下文跟踪。
     */
    @Bean
    public RequestInterceptor feignTraceInterceptor() {
        return new FeignTraceInterceptor();
    }
}
