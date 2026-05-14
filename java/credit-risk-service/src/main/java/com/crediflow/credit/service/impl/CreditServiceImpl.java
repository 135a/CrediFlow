package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.feign.AgentClient;
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

    @Override
    public com.crediflow.credit.entity.CreditApplication applyCredit(Long userId) {
        // 0. KYC v2 + 主卡校验（OpenSpec: kyc-realname-face-bankcard-rebuild）
        Result<Map<String, Object>> eligibility = userClient.getEligibility(userId);
        if (eligibility == null || eligibility.getData() == null) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED, "请先完成 KYC 实名实人核验");
        }
        Object kycPassedObj = eligibility.getData().get("kycPassed");
        Object hasPrimaryObj = eligibility.getData().get("hasPrimaryBankCard");
        boolean kycPassed = Boolean.TRUE.equals(kycPassedObj);
        boolean hasPrimary = Boolean.TRUE.equals(hasPrimaryObj);
        if (!kycPassed) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED,
                    ErrorCode.KYC_FACE_NOT_VERIFIED.getMessage());
        }
        if (!hasPrimary) {
            throw new BusinessException(ErrorCode.KYC_BANKCARD_REQUIRED,
                    ErrorCode.KYC_BANKCARD_REQUIRED.getMessage());
        }

        // KYC 通过后再读旧 status 接口拿画像字段（仅用于风控建模上下文，不再作为门禁）
        Result<Map<String, Object>> kycResult = userClient.getKycStatus(userId);
        Map<String, Object> kycData = kycResult == null || kycResult.getData() == null
                ? Map.of() : kycResult.getData();

        // 1. 检查是否已有活跃授信
        CreditResult existing = getActiveCredit(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户已存在有效授信，无需重复申请");
        }

        // 2. 初始化申请单
        com.crediflow.credit.entity.CreditApplication application = new com.crediflow.credit.entity.CreditApplication();
        application.setUserId(userId);
        application.setApplyAmount(new BigDecimal("5000.00")); // 占位额度
        application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_PENDING_HARD_RULES);
        application.setCreatedAt(new Date());
        application.setUpdatedAt(new Date());
        creditApplicationService.save(application);

        // 3. 执行硬规则 (Phase 1.1)
        HardRuleResult hardRuleResult = hardRuleEngine.evaluate(userId);
        if (!hardRuleResult.isPassed()) {
            application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_REJECTED);
            application.setAuditReason(hardRuleResult.getRejectCode() + ": " + hardRuleResult.getAuditDetail());
            
            try {
                // 7.2 Async or sync call to Agent for insight
                Map<String, Object> req = Map.of("ruleSummaries", java.util.List.of(hardRuleResult.getRejectCode()));
                Map<String, Object> insight = agentClient.creditRejectionInsight(req);
                application.setRiskInsight((String) insight.get("adminInsight"));
                application.setUserSafeInsight((String) insight.get("userSafeInsight"));
            } catch (Exception e) {
                log.error("Failed to fetch rejection insight from Agent", e);
                application.setUserSafeInsight("综合评估未通过，请保持良好信用记录后重试");
            }
            
            application.setUpdatedAt(new Date());
            creditApplicationService.updateById(application);
            return application;
        }

        // 4. 执行评分 (Phase 1.2 & 1.3)
        application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_PENDING_SCORING);
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
        application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_PENDING_ROUTING);
        creditApplicationService.updateById(application);
        
        if ("LOW".equals(scoreDetail.getRiskLevel())) {
            // 低风险直过
            application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_APPROVED);
            application.setSecondaryFaceRequired(false);
            application.setUpdatedAt(new Date());
            creditApplicationService.updateById(application);
            
            generateUserQuota(userId, scoreDetail.getTotalScore());
        } else {
            // 中风险/高风险，强制二次人脸
            application.setStatus(com.crediflow.credit.entity.CreditApplication.STATUS_PENDING_SECONDARY_FACE);
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
                .eq(CreditResult::getStatus, "ACTIVE")
                .gt(CreditResult::getExpireTime, new Date())
                .last("LIMIT 1"));
    }

    @Override
    public void manualApprove(Long applicationId, String remark) {
        com.crediflow.credit.entity.CreditApplication application = creditApplicationService.getById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请记录不存在");
        }
        if (!"REJECTED".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅允许对已拒绝的申请进行干预");
        }

        // 修改状态为通过
        application.setStatus("APPROVED");
        application.setAuditReason(application.getAuditReason() + " | [人工审核强制通过]: " + remark);
        application.setUpdatedAt(new Date());
        creditApplicationService.updateById(application);

        // 生成最终额度
        CreditResult result = new CreditResult();
        result.setUserId(application.getUserId());
        result.setCreditAmount(application.getSuggestedAmount() != null ? application.getSuggestedAmount() : new BigDecimal("5000.00"));
        result.setUsedAmount(BigDecimal.ZERO);
        result.setStatus("ACTIVE");
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
}
