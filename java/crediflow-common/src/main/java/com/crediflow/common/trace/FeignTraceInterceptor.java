package com.crediflow.common.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;
public class FeignTraceInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceIdContext.getTraceId();
        if (traceId != null) {
            template.header(RequestTraceFilter.TRACE_ID_HEADER, traceId);
        }
    }
}
