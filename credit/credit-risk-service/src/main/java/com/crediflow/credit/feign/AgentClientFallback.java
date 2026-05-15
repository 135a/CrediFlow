package com.crediflow.credit.feign;

import com.crediflow.credit.constants.AgentFallbackMessages;
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

    // 从配置中获取默认降级概率，默认值为0.99
    @Value("${crediflow.agent.fallback.default-probability:0.99}")
    private double fallbackDefaultProbability;

    // 从配置中获取欺诈降级概率，默认值为0.99
    @Value("${crediflow.agent.fallback.fraud-probability:0.99}")
    private double fallbackFraudProbability;

    /**
     * 人工审核助手方法的降级处理
     * @param data 人工审核请求参数
     * @return 降级后的人工审核响应
     */
    @Override
    public ManualReviewAssistantResponse manualReviewAssistant(ManualReviewAssistantRequest data) {
        // 记录降级日志，包含用户ID信息
        log.warn("[agent-fallback] manualReviewAssistant userId={}", data != null ? data.getUserId() : null);
        // 创建并设置人工审核助手响应对象
        ManualReviewAssistantResponse resp = new ManualReviewAssistantResponse();
        resp.setRiskDetails(List.of(AgentFallbackMessages.RISK_DETAIL_TIMEOUT));
        resp.setDefaultProbability(fallbackDefaultProbability);
        resp.setFraudProbability(fallbackFraudProbability);
        resp.setSuggestion(AgentFallbackMessages.SUGGESTION_REJECT);
        return resp;
    }

    /**
     * 拒绝洞察方法的降级处理
     * @param data 拒绝洞察请求参数
     * @return 降级后的拒绝洞察响应
     */
    @Override
    public CreditRejectionInsightResponse creditRejectionInsight(CreditRejectionInsightRequest data) {
        // 记录降级日志
        log.warn("[agent-fallback] creditRejectionInsight");
        // 创建并设置拒绝洞察响应对象
        CreditRejectionInsightResponse resp = new CreditRejectionInsightResponse();
        resp.setUserSafeInsight(AgentFallbackMessages.USER_SAFE_INSIGHT);
        resp.setAdminInsight(AgentFallbackMessages.ADMIN_INSIGHT_TIMEOUT);
        resp.setActionableAdvice(AgentFallbackMessages.ACTIONABLE_ADVICE);
        return resp;
    }
}
