package com.crediflow.credit.service.impl;

import com.crediflow.credit.entity.CreditReviewQueue;
import com.crediflow.credit.feign.AgentClient;
import com.crediflow.credit.mapper.CreditReviewQueueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ManualReviewAsyncService {

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private CreditReviewQueueMapper creditReviewQueueMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Async
    public void generateManualReviewAssistant(Long applicationId, Long userId, double totalScore, String riskLevel) {
        generateManualReviewAssistantWithScene(applicationId, userId, totalScore, riskLevel, "CREDIT");
    }

    @Async
    public void generateManualReviewAssistantWithScene(Long applicationId, Long userId, double totalScore, String riskLevel, String sceneType) {
        log.info("Starting async manual review assistant for applicationId: {} sceneType: {}", applicationId, sceneType);
        
        try {
            Map<String, Object> reqData = new HashMap<>();
            reqData.put("userId", userId);
            reqData.put("sceneType", sceneType);
            
            Map<String, Object> scoreDetail = new HashMap<>();
            scoreDetail.put("totalScore", totalScore);
            scoreDetail.put("riskLevel", riskLevel);
            reqData.put("scoreDetail", scoreDetail);
            
            Map<String, Object> result = agentClient.manualReviewAssistant(reqData);
            
            CreditReviewQueue queue = new CreditReviewQueue();
            queue.setApplicationId(applicationId);
            queue.setUserId(userId);
            queue.setSceneType(sceneType);
            queue.setRiskDetails(objectMapper.writeValueAsString(result.get("riskDetails")));
            queue.setDefaultProbability((Double) result.get("defaultProbability"));
            queue.setFraudProbability((Double) result.get("fraudProbability"));
            queue.setAiSuggestion((String) result.get("suggestion"));
            queue.setStatus("PENDING");
            queue.setCreatedAt(new Date());
            queue.setUpdatedAt(new Date());
            
            creditReviewQueueMapper.insert(queue);
            log.info("Successfully generated manual review assistant for applicationId: {}", applicationId);
            
        } catch (Exception e) {
            log.error("Error generating manual review assistant for applicationId: {}", applicationId, e);
            // Even if AI fails, we must create a queue entry so human can review
            CreditReviewQueue queue = new CreditReviewQueue();
            queue.setApplicationId(applicationId);
            queue.setUserId(userId);
            queue.setSceneType(sceneType);
            queue.setRiskDetails("[\"AI分析失败，请人工排查\"]");
            queue.setDefaultProbability(0.99);
            queue.setFraudProbability(0.99);
            queue.setAiSuggestion("建议拒绝");
            queue.setStatus("PENDING");
            queue.setCreatedAt(new Date());
            queue.setUpdatedAt(new Date());
            creditReviewQueueMapper.insert(queue);
        }
    }
}
