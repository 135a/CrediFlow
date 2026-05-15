package com.crediflow.credit.feign;

import com.crediflow.credit.feign.dto.CreditRejectionInsightRequest;
import com.crediflow.credit.feign.dto.CreditRejectionInsightResponse;
import com.crediflow.credit.feign.dto.ManualReviewAssistantRequest;
import com.crediflow.credit.feign.dto.ManualReviewAssistantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 与 agent-service 通信；请求/响应使用强类型 DTO，降级见 {@link AgentClientFallback}。
 */
@FeignClient(name = "agent-service", fallback = AgentClientFallback.class)
public interface AgentClient {

    @PostMapping("/manual_review_assistant")
    ManualReviewAssistantResponse manualReviewAssistant(@RequestBody ManualReviewAssistantRequest data);

    @PostMapping("/credit_rejection_insight")
    CreditRejectionInsightResponse creditRejectionInsight(@RequestBody CreditRejectionInsightRequest data);
}
