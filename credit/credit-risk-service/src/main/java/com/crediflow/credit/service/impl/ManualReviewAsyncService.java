package com.crediflow.credit.service.impl;

import com.crediflow.credit.entity.CreditReviewQueue;
import com.crediflow.credit.enums.ReviewQueueStatus;
import com.crediflow.credit.enums.ReviewSceneType;
import com.crediflow.credit.feign.AgentClient;
import com.crediflow.credit.feign.dto.ManualReviewAssistantRequest;
import com.crediflow.credit.feign.dto.ManualReviewAssistantResponse;
import com.crediflow.credit.feign.dto.ManualReviewScoreDetail;
import com.crediflow.credit.mapper.CreditReviewQueueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class ManualReviewAsyncService {

    @Value("${crediflow.agent.fallback.default-probability:0.99}")
    private double fallbackDefaultProbability;

    @Value("${crediflow.agent.fallback.fraud-probability:0.99}")
    private double fallbackFraudProbability;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private CreditReviewQueueMapper creditReviewQueueMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Async
    public void generateManualReviewAssistant(Long applicationId, Long userId, double totalScore, String riskLevel) {
        generateManualReviewAssistantWithScene(applicationId, userId, totalScore, riskLevel, ReviewSceneType.CREDIT.getCode());
    }

    @Async
    public void generateManualReviewAssistantWithScene(Long applicationId, Long userId, double totalScore,
                                                       String riskLevel, String sceneType) {
        log.info("Starting async manual review assistant for applicationId: {} sceneType: {}", applicationId, sceneType);

        try {
            ManualReviewAssistantRequest req = new ManualReviewAssistantRequest();
            req.setUserId(userId);
            req.setSceneType(sceneType);
            req.setScoreDetail(new ManualReviewScoreDetail(totalScore, riskLevel));

            ManualReviewAssistantResponse result = agentClient.manualReviewAssistant(req);
            insertQueue(applicationId, userId, ReviewSceneType.fromCode(sceneType), result, null);
            log.info("Successfully generated manual review assistant for applicationId: {}", applicationId);
        } catch (Exception e) {
            log.error("Error generating manual review assistant for applicationId: {}", applicationId, e);
            insertQueueOnFailure(applicationId, userId, ReviewSceneType.fromCode(sceneType));
        }
    }

    private void insertQueue(Long applicationId, Long userId, ReviewSceneType sceneType,
                             ManualReviewAssistantResponse result, String fallbackRiskJson) throws Exception {
        CreditReviewQueue queue = new CreditReviewQueue();
        queue.setApplicationId(applicationId);
        queue.setUserId(userId);
        queue.setSceneType(sceneType);
        if (result != null) {
            queue.setRiskDetails(objectMapper.writeValueAsString(result.getRiskDetails()));
            queue.setDefaultProbability(result.getDefaultProbability());
            queue.setFraudProbability(result.getFraudProbability());
            queue.setAiSuggestion(result.getSuggestion());
        } else {
            queue.setRiskDetails(fallbackRiskJson);
            queue.setDefaultProbability(fallbackDefaultProbability);
            queue.setFraudProbability(fallbackFraudProbability);
            queue.setAiSuggestion("建议拒绝");
        }
        queue.setStatus(ReviewQueueStatus.PENDING);
        queue.setCreatedAt(new Date());
        queue.setUpdatedAt(new Date());
        creditReviewQueueMapper.insert(queue);
    }

    private void insertQueueOnFailure(Long applicationId, Long userId, ReviewSceneType sceneType) {
        try {
            insertQueue(applicationId, userId, sceneType, null, "[\"AI分析失败，请人工排查\"]");
        } catch (Exception ex) {
            log.error("Failed to insert fallback review queue for applicationId: {}", applicationId, ex);
        }
    }
}
