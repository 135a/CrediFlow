package com.crediflow.credit.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "agent-service", fallback = AgentClientFallback.class)
public interface AgentClient {
    
    @PostMapping("/api/v1/credit/evaluate")
    Map<String, Object> evaluateRisk(@RequestBody Map<String, Object> userData);

    @PostMapping("/manual_review_assistant")
    Map<String, Object> manualReviewAssistant(@RequestBody Map<String, Object> data);

    @PostMapping("/credit_rejection_insight")
    Map<String, Object> creditRejectionInsight(@RequestBody Map<String, Object> data);
}

@Component
class AgentClientFallback implements AgentClient {
    @Override
    public Map<String, Object> evaluateRisk(Map<String, Object> userData) {
        // 超时降级逻辑：返回默认基础额度或触发人工审核
        return Map.of(
            "status", "FALLBACK",
            "suggestedAmount", 5000.00,
            "reason", "Agent服务超时，采用降级策略"
        );
    }

    @Override
    public Map<String, Object> manualReviewAssistant(Map<String, Object> data) {
        return Map.of(
            "riskDetails", java.util.List.of("获取风险明细超时"),
            "defaultProbability", 0.99,
            "fraudProbability", 0.99,
            "suggestion", "建议拒绝"
        );
    }

    @Override
    public Map<String, Object> creditRejectionInsight(Map<String, Object> data) {
        return Map.of(
            "userSafeInsight", "综合评估未通过，请保持良好信用记录后重试",
            "adminInsight", "Agent超时未返回洞察",
            "actionableAdvice", "建议三个月后再试"
        );
    }
}
