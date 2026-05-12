package com.crediflow.common.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class InternalAuthInterceptor implements RequestInterceptor {

    public static final String INTERNAL_AUTH_HEADER = "X-Internal-Token";

    @Override
    public void apply(RequestTemplate template) {
        template.header(INTERNAL_AUTH_HEADER, JwtUtils.generateInternalToken());
    }
}
