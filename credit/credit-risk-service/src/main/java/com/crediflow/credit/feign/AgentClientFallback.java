package com.crediflow.credit.feign;

import com.crediflow.credit.feign.dto.CreditRejectionInsightRequest;
import com.crediflow.credit.feign.dto.CreditRejectionInsightResponse;
import com.crediflow.credit.feign.dto.ManualReviewAssistantRequest;
import com.crediflow.credit.feign.dto.ManualReviewAssistantResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 不可用时的降级响应；阈值来自配置，避免魔法数散落。
 */
@Slf4j
@Component
public class AgentClientFallback implements AgentClient {

    @Value("${crediflow.agent.fallback.default-probability:0.99}")
    private double fallbackDefaultProbability;

    @Value("${crediflow.agent.fallback.fraud-probability:0.99}")
    private double fallbackFraudProbability;

    @Override
    public ManualReviewAssistantResponse manualReviewAssistant(ManualReviewAssistantRequest data) {
        log.warn("[agent-fallback] manualReviewAssistant userId={}", data != null ? data.getUserId() : null);
        ManualReviewAssistantResponse resp = new ManualReviewAssistantResponse();
        resp.setRiskDetails(List.of("获取风险明细超时"));
        resp.setDefaultProbability(fallbackDefaultProbability);
        resp.setFraudProbability(fallbackFraudProbability);
        resp.setSuggestion("建议拒绝");
        return resp;
    }

    @Override
    public CreditRejectionInsightResponse creditRejectionInsight(CreditRejectionInsightRequest data) {
        log.warn("[agent-fallback] creditRejectionInsight");
        CreditRejectionInsightResponse resp = new CreditRejectionInsightResponse();
        resp.setUserSafeInsight("综合评估未通过，请保持良好信用记录后重试");
        resp.setAdminInsight("Agent超时未返回洞察");
        resp.setActionableAdvice("建议三个月后再试");
        return resp;
    }
}
