package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.credit.dto.FaceCallbackRequest;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.entity.CreditScore;
import com.crediflow.credit.enums.CreditApplicationStatus;
import com.crediflow.credit.enums.ModelRiskLevel;
import com.crediflow.credit.mapper.CreditScoreMapper;
import com.crediflow.credit.service.CreditApplicationService;
import com.crediflow.credit.service.CreditService;
import com.crediflow.credit.service.FaceLivenessCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
public class FaceLivenessCallbackServiceImpl implements FaceLivenessCallbackService {

    private static final double DEFAULT_TOTAL_SCORE_MEDIUM_BRANCH = 80.0;
    private static final double DEFAULT_TOTAL_SCORE_HIGH_BRANCH = 50.0;

    @Autowired
    private CreditApplicationService creditApplicationService;

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditScoreMapper creditScoreMapper;

    @Autowired
    private ManualReviewAsyncService manualReviewAsyncService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(FaceCallbackRequest request) {
        log.info("处理人脸活体回调 applicationId={}, passed={}", request.getApplicationId(), request.isPassed());

        Long applicationPk = parseApplicationPk(request.getApplicationId());
        if (applicationPk == null) {
            log.warn("人脸回调 applicationId 非法: {}", request.getApplicationId());
            return;
        }

        CreditApplication application = creditApplicationService.getById(applicationPk);
        if (application == null || application.getStatus() != CreditApplicationStatus.PENDING_SECONDARY_FACE) {
            log.warn("人脸回调申请不存在或状态非待二次人脸: id={}", applicationPk);
            return;
        }

        if (request.isPassed()) {
            applyPassedBranch(application);
        } else {
            application.setStatus(CreditApplicationStatus.REJECTED);
            application.setAuditReason("Secondary face verification failed: " + request.getErrorMessage());
        }

        application.setUpdatedAt(new Date());
        creditApplicationService.updateById(application);
    }

    private void applyPassedBranch(CreditApplication application) {
        ModelRiskLevel risk = ModelRiskLevel.fromCode(application.getModelRiskLevel());
        if (risk == ModelRiskLevel.MEDIUM) {
            application.setStatus(CreditApplicationStatus.APPROVED);
            double totalScore = totalScoreOrDefault(loadScore(application), DEFAULT_TOTAL_SCORE_MEDIUM_BRANCH);
            creditService.generateUserQuota(application.getUserId(), totalScore);
        } else if (risk == ModelRiskLevel.HIGH) {
            application.setStatus(CreditApplicationStatus.PENDING_MANUAL_REVIEW);
            double totalScore = totalScoreOrDefault(loadScore(application), DEFAULT_TOTAL_SCORE_HIGH_BRANCH);
            manualReviewAsyncService.generateManualReviewAssistant(
                    application.getId(), application.getUserId(), totalScore, application.getModelRiskLevel());
        } else {
            application.setStatus(CreditApplicationStatus.APPROVED);
        }
    }

    private CreditScore loadScore(CreditApplication application) {
        LambdaQueryWrapper<CreditScore> query = new LambdaQueryWrapper<>();
        query.eq(CreditScore::getApplicationId, String.valueOf(application.getId()));
        return creditScoreMapper.selectOne(query);
    }

    private static double totalScoreOrDefault(CreditScore score, double defaultIfMissing) {
        return score != null ? score.getTotalScore() : defaultIfMissing;
    }

    private static Long parseApplicationPk(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
