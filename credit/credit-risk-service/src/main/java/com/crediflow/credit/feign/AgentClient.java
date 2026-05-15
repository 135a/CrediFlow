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
 * 该接口是一个 Feign 客户端，用于与名为 "agent-service" 的服务进行通信。
 * 当服务调用失败时，会回退到 AgentClientFallback 类中定义的降级逻辑。
 */
@FeignClient(name = "agent-service", fallback = AgentClientFallback.class)
public interface AgentClient {

    /**
     * 手动审核助手接口
     * @param data 包含手动审核请求数据的 ManualReviewAssistantRequest 对象
     * @return 返回 ManualReviewAssistantResponse 对象，包含手动审核的响应结果
     */
    @PostMapping("/manual_review_assistant")
    ManualReviewAssistantResponse manualReviewAssistant(@RequestBody ManualReviewAssistantRequest data);

    /**
     * 拒绝洞察接口
     * @param data 包含拒绝洞察请求数据的 CreditRejectionInsightRequest 对象
     * @return 返回 CreditRejectionInsightResponse 对象，包含拒绝洞察的响应结果
     */
    @PostMapping("/credit_rejection_insight")
    CreditRejectionInsightResponse creditRejectionInsight(@RequestBody CreditRejectionInsightRequest data);
}
