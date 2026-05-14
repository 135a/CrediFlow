package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/credit/face")
public class FaceCallbackController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.crediflow.credit.service.CreditApplicationService applicationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.crediflow.credit.service.CreditService creditService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.crediflow.credit.mapper.CreditScoreMapper creditScoreMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.crediflow.credit.service.impl.ManualReviewAsyncService manualReviewAsyncService;

    @PostMapping("/callback")
    public Result<Void> handleFaceCallback(@RequestBody FaceCallbackRequest request) {
        log.info("Received face liveness callback for applicationId: {}, passed: {}", 
                 request.getApplicationId(), request.isPassed());
                 
        com.crediflow.credit.entity.CreditApplication application = applicationService.getById(request.getApplicationId());
        if (application == null || !com.crediflow.credit.entity.CreditApplication.STATUS_PENDING_SECONDARY_FACE.equals(application.getStatus())) {
            log.warn("Invalid face callback or application state for id: {}", request.getApplicationId());
            return Result.success();
        }

        if (request.isPassed()) {
            if ("MEDIUM".equals(application.getModelRiskLevel())) {
                application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_APPROVED);
                // Get the total score. For simplicity, assuming we fetch it from CreditScoreMapper (in practice we'd query it).
                // But for now, we just fetch the score record
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.CreditScore> query = 
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                query.eq(com.crediflow.credit.entity.CreditScore::getApplicationId, String.valueOf(application.getId()));
                com.crediflow.credit.entity.CreditScore score = creditScoreMapper.selectOne(query);
                double totalScore = score != null ? score.getTotalScore() : 80.0;
                creditService.generateUserQuota(application.getUserId(), totalScore);
            } else if ("HIGH".equals(application.getModelRiskLevel())) {
                application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_PENDING_MANUAL_REVIEW);
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.CreditScore> query = 
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                query.eq(com.crediflow.credit.entity.CreditScore::getApplicationId, String.valueOf(application.getId()));
                com.crediflow.credit.entity.CreditScore score = creditScoreMapper.selectOne(query);
                double totalScore = score != null ? score.getTotalScore() : 50.0;
                // Use application context to avoid cyclic dependency if needed, or inject directly
                manualReviewAsyncService.generateManualReviewAssistant(application.getId(), application.getUserId(), totalScore, application.getModelRiskLevel());
            } else {
                application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_APPROVED);
            }
        } else {
            application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_REJECTED);
            application.setAuditReason("Secondary face verification failed: " + request.getErrorMessage());
        }

        application.setUpdatedAt(new java.util.Date());
        applicationService.updateById(application);
        
        return Result.success();
    }

    @Data
    public static class FaceCallbackRequest {
        private String applicationId;
        private Long userId;
        private boolean passed;
        private String errorMessage;
    }
}
