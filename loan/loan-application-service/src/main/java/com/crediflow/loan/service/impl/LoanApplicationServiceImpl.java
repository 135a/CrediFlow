package com.crediflow.loan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.annotation.Idempotent;
import com.crediflow.common.api.credit.CreditResultResponse;
import com.crediflow.common.api.credit.LoanReviewEnqueueRequest;
import com.crediflow.common.api.credit.LoanRiskEvaluateRequest;
import com.crediflow.common.api.user.UserEligibilityResponse;
import com.crediflow.common.event.LoanLifecycleMessage;
import com.crediflow.common.event.MqConstants;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.loan.entity.LoanApplication;
import com.crediflow.loan.entity.LocalMessage;
import com.crediflow.loan.feign.ContractClient;
import com.crediflow.loan.feign.CreditClient;
import com.crediflow.loan.feign.UserClient;
import com.crediflow.loan.mapper.LoanApplicationMapper;
import com.crediflow.loan.mapper.LocalMessageMapper;
import com.crediflow.loan.service.LoanApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LoanApplicationServiceImpl extends ServiceImpl<LoanApplicationMapper, LoanApplication> implements LoanApplicationService {

    private static final Set<Integer> ALLOWED_TERMS = Set.of(3, 6, 12);

    @Autowired
    private CreditClient creditClient;

    @Autowired
    private UserClient userClient;

    @Autowired
    private ContractClient contractClient;

    @Autowired
    private LocalMessageMapper localMessageMapper;

    @Override
    @Idempotent(key = "'LOAN_APP:' + #idempotencyToken")
    public LoanApplication applyLoan(Long userId, BigDecimal applyAmount, Integer term, String idempotencyToken) {
        validateTerm(term);
        checkUserEligibility(userId);
        checkCreditLimit(userId, applyAmount);
        checkContractStatus(userId);

        LoanApplication application = createApplicationRecord(userId, applyAmount, term);
        return evaluateRiskAndRoute(application);
    }

    private void validateTerm(Integer term) {
        if (term == null || !ALLOWED_TERMS.contains(term)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不支持的分期期数，仅支持 3, 6, 12 期");
        }
    }

    private void checkUserEligibility(Long userId) {
        Result<UserEligibilityResponse> eligibility = userClient.getEligibility(userId);
        if (eligibility == null || eligibility.getData() == null) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED, "请先完成 KYC 实名实人核验");
        }
        UserEligibilityResponse eligibilityInfo = eligibility.getData();
        if (!eligibilityInfo.isKycPassed()) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED,
                    ErrorCode.KYC_FACE_NOT_VERIFIED.getMessage());
        }
        if (!eligibilityInfo.isHasPrimaryBankCard()) {
            throw new BusinessException(ErrorCode.KYC_BANKCARD_REQUIRED,
                    ErrorCode.KYC_BANKCARD_REQUIRED.getMessage());
        }
    }

    private void checkCreditLimit(Long userId, BigDecimal applyAmount) {
        Result<CreditResultResponse> creditResult = creditClient.getActiveCreditInternal(userId);
        if (creditResult == null || creditResult.getCode() != 200 || creditResult.getData() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无有效授信，请先申请额度");
        }

        CreditResultResponse creditData = creditResult.getData();
        BigDecimal availableAmount = creditData.getCreditAmount().subtract(creditData.getUsedAmount());

        if (applyAmount.compareTo(availableAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请金额超过可用额度");
        }
    }

    private void checkContractStatus(Long userId) {
        Result<Map<String, Object>> contractResult = contractClient.getCreditContractStatus(userId);
        if (contractResult == null || contractResult.getCode() != 200 || contractResult.getData() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法获取授信合同状态");
        }
        
        String contractStatus = (String) contractResult.getData().get("status");
        if (!"SIGNED".equals(contractStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "必须先签署授信协议才能借款");
        }
    }

    private LoanApplication createApplicationRecord(Long userId, BigDecimal applyAmount, Integer term) {
        LoanApplication application = new LoanApplication();
        application.setApplicationNo("LOAN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        application.setUserId(userId);
        application.setApplyAmount(applyAmount);
        application.setTerm(term);
        application.setStatus("PENDING_RISK");
        application.setCreatedAt(new Date());
        application.setUpdatedAt(new Date());
        this.save(application);
        return application;
    }

    private LoanApplication evaluateRiskAndRoute(LoanApplication application) {
        LoanRiskEvaluateRequest evalReq = new LoanRiskEvaluateRequest();
        evalReq.setUserId(application.getUserId());
        evalReq.setApplicationId(application.getId());
        evalReq.setApplyAmount(application.getApplyAmount());

        Result<String> evalResult = creditClient.evaluateLoanRisk(evalReq);
        if (evalResult == null || evalResult.getCode() != 200) {
            application.setStatus("REJECTED");
            this.updateById(application);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, evalResult != null ? evalResult.getMessage() : "风控评估失败");
        }
        
        String riskLevel = evalResult.getData();
        application.setRiskLevel(riskLevel);
        
        if ("REJECTED".equals(riskLevel)) {
            application.setStatus("REJECTED");
            this.updateById(application);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "风控拦截，暂不符合借款条件");
        } else if ("MANUAL_REVIEW".equals(riskLevel)) {
            application.setStatus("PENDING_MANUAL_REVIEW");
            this.updateById(application);
            
            LoanReviewEnqueueRequest enqueueReq = new LoanReviewEnqueueRequest();
            enqueueReq.setApplicationId(application.getId());
            enqueueReq.setUserId(application.getUserId());
            enqueueReq.setSceneType("LOAN");
            creditClient.enqueueLoanReview(enqueueReq);
        } else if ("LOW".equals(riskLevel)) {
            application.setStatus("CONTRACT_PROCESSING");
            this.updateById(application);
            this.approve(application.getId());
        } else if ("MEDIUM".equals(riskLevel) || "HIGH".equals(riskLevel)) {
            application.setStatus("PENDING_FACE");
            this.updateById(application);
        }

        return application;
    }

    @Override
    @Transactional
    public void approve(Long applicationId) {
        LoanApplication application = this.getById(applicationId);
        if (!"PENDING".equals(application.getStatus()) && !"PENDING_RISK".equals(application.getStatus()) && !"PENDING_MANUAL_REVIEW".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请单状态非法，无法通过");
        }
        
        application.setStatus("CONTRACT_PROCESSING");
        this.updateById(application);
        
        LoanLifecycleMessage message = new LoanLifecycleMessage();
        message.setLoanApplicationId(application.getId());
        message.setUserId(application.getUserId());
        message.setEventType(MqConstants.TAG_LOAN_APPROVED);
        
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("applyAmount", application.getApplyAmount());
        payloadMap.put("term", application.getTerm());
        message.setPayload(payloadMap);
        
        try {
            LocalMessage localMsg = new LocalMessage();
            localMsg.setTopic(MqConstants.TOPIC_LOAN_LIFECYCLE);
            localMsg.setTag(MqConstants.TAG_LOAN_APPROVED);
            localMsg.setPayload(new ObjectMapper().writeValueAsString(message));
            localMsg.setStatus("NEW");
            localMsg.setCreatedAt(new Date());
            localMsg.setUpdatedAt(new Date());
            localMessageMapper.insert(localMsg);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to save local message");
        }
    }

    @Override
    public void reject(Long applicationId, String reason) {
        LoanApplication application = this.getById(applicationId);
        if (!"PENDING".equals(application.getStatus()) && !"PENDING_RISK".equals(application.getStatus()) && !"PENDING_MANUAL_REVIEW".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请单状态非法，无法拒绝");
        }
        application.setStatus("REJECTED");
        this.updateById(application);
    }
    
    @Override
    public IPage<Map<String, Object>> listApplications(Integer page, Integer size, String status) {
        Page<LoanApplication> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<LoanApplication> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            query.eq(LoanApplication::getStatus, status);
        }
        query.orderByDesc(LoanApplication::getCreatedAt);
        IPage<LoanApplication> resultPage = this.page(pageParam, query);
        return resultPage.convert(this::convertToMap);
    }

    @Override
    public IPage<Map<String, Object>> listAdminApplications(long current, long size, Date startTime, Date endTime, String phone) {
        LambdaQueryWrapper<LoanApplication> queryWrapper = new LambdaQueryWrapper<>();

        if (phone != null && !phone.trim().isEmpty()) {
            Result<Long> userRes = userClient.getUserIdByPhone(phone);
            if (userRes == null || userRes.getData() == null) {
                return new Page<>(current, size);
            }
            queryWrapper.eq(LoanApplication::getUserId, userRes.getData());
        }

        if (startTime != null) {
            queryWrapper.ge(LoanApplication::getCreatedAt, startTime);
        }
        if (endTime != null) {
            queryWrapper.le(LoanApplication::getCreatedAt, endTime);
        }
        queryWrapper.orderByDesc(LoanApplication::getCreatedAt);

        Page<LoanApplication> page = new Page<>(current, size);
        IPage<LoanApplication> resultPage = this.page(page, queryWrapper);
        return resultPage.convert(this::convertToMap);
    }

    @Override
    public Map<String, Object> getApplicationMap(Long id) {
        LoanApplication application = this.getById(id);
        if (application == null) {
            return null;
        }
        return convertToMap(application);
    }
    
    @Override
    @Transactional
    public void manualReview(Long id, String action, String reason, Long reviewerId) {
        LoanApplication application = this.getById(id);
        if (application == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请单不存在");
        }
        if (!"PENDING_MANUAL_REVIEW".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "非待人工审核状态，无法操作");
        }
        
        application.setManualReviewerId(reviewerId);
        application.setManualReviewReason(reason);
        application.setManualReviewTime(new Date());
        application.setUpdatedAt(new Date());
        this.updateById(application);

        if ("APPROVE".equalsIgnoreCase(action)) {
            this.approve(id);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            this.reject(id, reason);
        } else {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "未知的审核动作");
        }
    }

    private Map<String, Object> convertToMap(LoanApplication application) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", application.getId());
        map.put("applicationNo", application.getApplicationNo());
        map.put("userId", application.getUserId());
        map.put("applyAmount", application.getApplyAmount());
        map.put("term", application.getTerm());
        map.put("status", application.getStatus());
        map.put("riskLevel", application.getRiskLevel());
        map.put("createdAt", application.getCreatedAt());
        map.put("updatedAt", application.getUpdatedAt());
        return map;
    }
}
