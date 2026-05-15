package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.api.user.UserEligibilityResponse;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.credit.dto.LoanReviewEnqueueRequest;
import com.crediflow.credit.dto.LoanRiskEvaluateRequest;
import com.crediflow.credit.dto.QuotaSummaryResponse;
import com.crediflow.credit.dto.RiskSignalEscalateRequest;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.entity.CreditReviewQueue;
import com.crediflow.credit.enums.CreditApplicationStatus;
import com.crediflow.credit.enums.CreditResultStatus;
import com.crediflow.credit.enums.ModelRiskLevel;
import com.crediflow.credit.enums.ReviewQueueStatus;
import com.crediflow.credit.enums.ReviewSceneType;
import com.crediflow.credit.feign.AgentClient;
import com.crediflow.credit.feign.dto.CreditRejectionInsightRequest;
import com.crediflow.credit.feign.dto.CreditRejectionInsightResponse;
import com.crediflow.credit.mapper.CreditResultMapper;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import com.crediflow.credit.feign.UserClient;
import com.crediflow.credit.service.rules.HardRuleEngine;
import com.crediflow.credit.service.rules.HardRuleResult;
import com.crediflow.credit.service.scoring.CreditScoringEngine;
import com.crediflow.credit.service.scoring.ScoreDetail;
import com.crediflow.credit.entity.CreditScore;
import com.crediflow.credit.mapper.CreditScoreMapper;
import com.crediflow.credit.entity.UserCreditQuota;
import com.crediflow.credit.mapper.UserCreditQuotaMapper;
import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CreditServiceImpl extends ServiceImpl<CreditResultMapper, CreditResult> implements CreditService {

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private UserClient userClient;

    @Autowired
    private com.crediflow.credit.service.CreditApplicationService creditApplicationService;

    @Autowired
    private HardRuleEngine hardRuleEngine;

    @Autowired
    private CreditScoringEngine creditScoringEngine;

    @Autowired
    private CreditScoreMapper creditScoreMapper;
    
    @Autowired
    private UserCreditQuotaMapper userCreditQuotaMapper;

    @Value("${crediflow.credit.app-url:http://localhost:8080}")
    private String appUrl;

    @Autowired
    private com.crediflow.credit.mapper.CreditReviewQueueMapper creditReviewQueueMapper;

    @Autowired
    private com.crediflow.credit.service.impl.ManualReviewAsyncService manualReviewAsyncService;

    @Override
    public CreditApplication applyCredit(Long userId) {
        Result<UserEligibilityResponse> eligibility = userClient.getEligibility(userId);
        if (eligibility == null || eligibility.getData() == null) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED, "请先完成 KYC 实名实人核验");
        }
        UserEligibilityResponse gate = eligibility.getData();
        if (!gate.isKycPassed()) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED,
                    ErrorCode.KYC_FACE_NOT_VERIFIED.getMessage());
        }
        if (!gate.isHasPrimaryBankCard()) {
            throw new BusinessException(ErrorCode.KYC_BANKCARD_REQUIRED,
                    ErrorCode.KYC_BANKCARD_REQUIRED.getMessage());
        }

        // 1. 检查是否已有活跃授信
        CreditResult existing = getActiveCredit(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户已存在有效授信，无需重复申请");
        }

        // 2. 初始化申请单
        CreditApplication application = new CreditApplication();
        application.setUserId(userId);
        application.setApplyAmount(new BigDecimal("5000.00")); // 占位额度
        application.setStatus(CreditApplicationStatus.PENDING_HARD_RULES);
        application.setCreatedAt(new Date());
        application.setUpdatedAt(new Date());
        creditApplicationService.save(application);

        // 3. 执行硬规则 (Phase 1.1)
        HardRuleResult hardRuleResult = hardRuleEngine.evaluate(userId);
        if (!hardRuleResult.isPassed()) {
            application.setStatus(CreditApplicationStatus.REJECTED);
            application.setAuditReason(hardRuleResult.getRejectCode() + ": " + hardRuleResult.getAuditDetail());
            
            try {
                CreditRejectionInsightRequest req = new CreditRejectionInsightRequest(
                        java.util.List.of(hardRuleResult.getRejectCode()));
                CreditRejectionInsightResponse insight = agentClient.creditRejectionInsight(req);
                application.setRiskInsight(insight.getAdminInsight());
                application.setUserSafeInsight(insight.getUserSafeInsight());
            } catch (Exception e) {
                log.error("Failed to fetch rejection insight from Agent", e);
                application.setUserSafeInsight("综合评估未通过，请保持良好信用记录后重试");
            }
            
            application.setUpdatedAt(new Date());
            creditApplicationService.updateById(application);
            return application;
        }

        // 4. 执行评分 (Phase 1.2 & 1.3)
        application.setStatus(CreditApplicationStatus.PENDING_SCORING);
        creditApplicationService.updateById(application);
        
        ScoreDetail scoreDetail = creditScoringEngine.calculateScore(userId);
        
        // 落库 cf_credit_score (Phase 1.4)
        CreditScore scoreRecord = new CreditScore();
        scoreRecord.setApplicationId(String.valueOf(application.getId()));
        scoreRecord.setS1Score(scoreDetail.getS1Score());
        scoreRecord.setS2Score(scoreDetail.getS2Score());
        scoreRecord.setS3Score(scoreDetail.getS3Score());
        scoreRecord.setS4Score(scoreDetail.getS4Score());
        scoreRecord.setTotalScore(scoreDetail.getTotalScore());
        scoreRecord.setRiskLevel(scoreDetail.getRiskLevel());
        scoreRecord.setRulesVersion(scoreDetail.getRulesVersion());
        scoreRecord.setCreatedAt(new Date());
        scoreRecord.setUpdatedAt(new Date());
        creditScoreMapper.insert(scoreRecord);

        application.setModelRiskLevel(scoreDetail.getRiskLevel());

        // 5. 确定性路由 (Phase 2.3)
        application.setStatus(CreditApplicationStatus.PENDING_ROUTING);
        creditApplicationService.updateById(application);
        
        if (ModelRiskLevel.LOW.getCode().equals(scoreDetail.getRiskLevel())) {
            application.setStatus(CreditApplicationStatus.APPROVED);
            application.setSecondaryFaceRequired(false);
            application.setUpdatedAt(new Date());
            creditApplicationService.updateById(application);
            
            generateUserQuota(userId, scoreDetail.getTotalScore());
        } else {
            // 中风险/高风险，强制二次人脸
            application.setStatus(CreditApplicationStatus.PENDING_SECONDARY_FACE);
            application.setSecondaryFaceRequired(true);
            application.setUpdatedAt(new Date());
            creditApplicationService.updateById(application);
            
            // 调用 user-service 发起二次人脸
            String callbackUrl = appUrl + "/api/internal/credit/face/callback";
            userClient.initFaceLiveness(userId, "CREDIT_SECONDARY_FACE", callbackUrl);
        }

        return application;
    }

    @Override
    public CreditResult getActiveCredit(Long userId) {
        return this.getOne(new LambdaQueryWrapper<CreditResult>()
                .eq(CreditResult::getUserId, userId)
                .eq(CreditResult::getStatus, CreditResultStatus.ACTIVE)
                .gt(CreditResult::getExpireTime, new Date())
                .last("LIMIT 1"));
    }

    @Override
    public void manualApprove(Long applicationId, String remark) {
        CreditApplication application = creditApplicationService.getById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请记录不存在");
        }
        if (application.getStatus() != CreditApplicationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅允许对已拒绝的申请进行干预");
        }

        application.setStatus(CreditApplicationStatus.APPROVED);
        application.setAuditReason(application.getAuditReason() + " | [人工审核强制通过]: " + remark);
        application.setUpdatedAt(new Date());
        creditApplicationService.updateById(application);

        // 生成最终额度
        CreditResult result = new CreditResult();
        result.setUserId(application.getUserId());
        result.setCreditAmount(application.getSuggestedAmount() != null ? application.getSuggestedAmount() : new BigDecimal("5000.00"));
        result.setUsedAmount(BigDecimal.ZERO);
        result.setStatus(CreditResultStatus.ACTIVE);
        result.setExpireTime(new Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000));
        result.setCreatedAt(new Date());
        result.setUpdatedAt(new Date());
        this.save(result);
    }
    
    @Override
    public void generateUserQuota(Long userId, double totalScore) {
        // 额度线性公式：UserQuota = MinQuota + (clamp(TotalScore, 60, 100) - 60) / 40 * (MaxQuota - MinQuota)
        BigDecimal minQuota = new BigDecimal("1000.00");
        BigDecimal maxQuota = new BigDecimal("50000.00");
        
        double clampedScore = Math.max(60, Math.min(100, totalScore));
        double factor = (clampedScore - 60.0) / 40.0;
        
        BigDecimal diff = maxQuota.subtract(minQuota);
        BigDecimal addAmount = diff.multiply(BigDecimal.valueOf(factor));
        BigDecimal finalQuota = minQuota.add(addAmount).setScale(2, java.math.RoundingMode.HALF_UP);
        
        // 写入循环额度账户表 cf_user_credit_quota
        UserCreditQuota quota = new UserCreditQuota();
        quota.setUserId(userId);
        quota.setTotalAmount(finalQuota);
        quota.setAvailableAmount(finalQuota);
        quota.setUsedAmount(BigDecimal.ZERO);
        quota.setFrozenAmount(BigDecimal.ZERO);
        quota.setVersion(0);
        quota.setCreatedAt(new Date());
        quota.setUpdatedAt(new Date());
        
        userCreditQuotaMapper.insert(quota);
    }

    @Override
    public void deductQuota(Long userId, java.math.BigDecimal amount) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCreditQuota> query = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            query.eq(UserCreditQuota::getUserId, userId).last("LIMIT 1");
            UserCreditQuota quota = userCreditQuotaMapper.selectOne(query);
            if (quota == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户额度不存在");
            }
            if (quota.getAvailableAmount().compareTo(amount) < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "可用额度不足");
            }
            
            int updated = userCreditQuotaMapper.update(null, 
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserCreditQuota>()
                    .eq(UserCreditQuota::getUserId, userId)
                    .eq(UserCreditQuota::getVersion, quota.getVersion())
                    .set(UserCreditQuota::getAvailableAmount, quota.getAvailableAmount().subtract(amount))
                    .set(UserCreditQuota::getUsedAmount, quota.getUsedAmount().add(amount))
                    .set(UserCreditQuota::getVersion, quota.getVersion() + 1));
                    
            if (updated > 0) {
                return; // success
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "系统繁忙，额度扣减失败，请稍后重试");
    }

    @Override
    public QuotaSummaryResponse getQuotaSummary(Long userId) {
        LambdaQueryWrapper<UserCreditQuota> query = new LambdaQueryWrapper<>();
        query.eq(UserCreditQuota::getUserId, userId).last("LIMIT 1");

        UserCreditQuota quota = userCreditQuotaMapper.selectOne(query);
        QuotaSummaryResponse resp = new QuotaSummaryResponse();
        if (quota != null) {
            resp.setTotalAmount(quota.getTotalAmount());
            resp.setAvailableAmount(quota.getAvailableAmount());
            resp.setUsedAmount(quota.getUsedAmount());
        }
        return resp;
    }

    @Override
    public void escalateRiskSignal(RiskSignalEscalateRequest request) {
        Long userId = request.getUserId();

        LambdaQueryWrapper<CreditApplication> query = new LambdaQueryWrapper<>();
        query.eq(CreditApplication::getUserId, userId)
             .orderByDesc(CreditApplication::getCreatedAt)
             .last("LIMIT 1");
        CreditApplication app = creditApplicationService.getOne(query);

        CreditReviewQueue queue = new CreditReviewQueue();
        queue.setUserId(userId);
        queue.setApplicationId(app != null ? app.getId() : 0L);
        queue.setSceneType(ReviewSceneType.CREDIT);
        queue.setRiskDetails("[\"对话意图预警：" + request.getRiskType()
                + "\", \"相关聊天记录：" + request.getRelevantChatLogs() + "\"]");
        queue.setDefaultProbability(0.85);
        queue.setFraudProbability(0.50);
        queue.setAiSuggestion(request.getAgentSuggestions());
        queue.setStatus(ReviewQueueStatus.PENDING);
        queue.setCreatedAt(new Date());
        queue.setUpdatedAt(new Date());

        creditReviewQueueMapper.insert(queue);
    }

    @Override
    public String evaluateLoanRisk(LoanRiskEvaluateRequest request) {
        Long userId = request.getUserId();

        boolean hasOverdue = false; // TODO: check actual overdue loans
        if (hasOverdue) {
            return "REJECTED";
        }

        java.time.LocalTime now = java.time.LocalTime.now();
        if (now.getHour() >= 1 && now.getHour() <= 4) {
            boolean highFrequency = false; // mock high frequency
            if (highFrequency) {
                return "MANUAL_REVIEW";
            }
        }

        ScoreDetail detail = creditScoringEngine.calculateLoanScore(userId);
        if ("HIGH".equals(detail.getRiskLevel())) {
            return "MANUAL_REVIEW";
        }

        return detail.getRiskLevel();
    }

    @Override
    public void enqueueLoanReview(LoanReviewEnqueueRequest request) {
        CreditReviewQueue queue = new CreditReviewQueue();
        queue.setApplicationId(request.getApplicationId());
        queue.setUserId(request.getUserId());
        queue.setSceneType(ReviewSceneType.fromCode(request.getSceneType()));
        queue.setStatus(ReviewQueueStatus.PENDING);
        queue.setCreatedAt(new Date());
        queue.setUpdatedAt(new Date());

        creditReviewQueueMapper.insert(queue);

        manualReviewAsyncService.generateManualReviewAssistantWithScene(
                request.getApplicationId(), request.getUserId(), 50.0, "HIGH", request.getSceneType());
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<CreditReviewQueue> listReviewQueue(long current, long size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CreditReviewQueue> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        LambdaQueryWrapper<CreditReviewQueue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreditReviewQueue::getStatus, ReviewQueueStatus.PENDING)
               .orderByAsc(CreditReviewQueue::getCreatedAt);
        return creditReviewQueueMapper.selectPage(page, wrapper);
    }
}
