package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 人脸验证回调控制器
 * 处理人脸活体检测的回调请求，根据验证结果更新申请状态
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/credit/face")
public class FaceCallbackController {

    // 信用申请服务，用于处理申请相关的业务逻辑
    @Autowired
    private com.crediflow.credit.service.CreditApplicationService applicationService;

    // 信用服务，用于处理信用相关的业务逻辑
    @Autowired
    private com.crediflow.credit.service.CreditService creditService;

    // 信用分数映射器，用于数据库操作
    @Autowired
    private com.crediflow.credit.mapper.CreditScoreMapper creditScoreMapper;

    // 人工审核异步服务，用于处理需要人工审核的业务
    @Autowired
    private com.crediflow.credit.service.impl.ManualReviewAsyncService manualReviewAsyncService;

    /**
     * 处理人脸验证回调
     * @param request 包含申请ID、用户ID、验证结果和错误信息
     * @return 返回操作结果
     */
    @PostMapping("/callback")
    public Result<Void> handleFaceCallback(@RequestBody FaceCallbackRequest request) {
        // 记录收到的人脸活体验证回调信息
        log.info("Received face liveness callback for applicationId: {}, passed: {}",
                 request.getApplicationId(), request.isPassed());
                 
        // 根据申请ID获取申请信息，并验证申请状态是否为待二次人脸验证
        com.crediflow.credit.entity.CreditApplication application = applicationService.getById(request.getApplicationId());
        if (application == null || !com.crediflow.credit.entity.CreditApplication.STATUS_PENDING_SECONDARY_FACE.equals(application.getStatus())) {
            log.warn("Invalid face callback or application state for id: {}", request.getApplicationId());
            return Result.success();
        }

        // 根据验证结果处理不同的业务逻辑
        if (request.isPassed()) {
            // 如果是中风险模型，直接批准申请
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
