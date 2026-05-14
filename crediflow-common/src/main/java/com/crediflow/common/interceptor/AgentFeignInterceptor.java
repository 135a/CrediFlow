package com.crediflow.common.interceptor;

import com.crediflow.common.web.AgentHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 跨 Agent 流转的 Feign 拦截器，自动注入 TraceId
 * 该拦截器用于在微服务调用链中传递追踪ID，实现请求追踪功能
 */
@Component
public class AgentFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 从 MDC 中获取当前链路的 TraceId
        String traceId = MDC.get(AgentHeaders.X_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(AgentHeaders.X_TRACE_ID, traceId);
        }
        // 向下游透传
        template.header(AgentHeaders.X_TRACE_ID, traceId);
    }
}
