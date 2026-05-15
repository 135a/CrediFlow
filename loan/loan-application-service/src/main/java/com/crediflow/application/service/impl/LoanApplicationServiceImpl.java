package com.crediflow.application.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.application.entity.LoanApplication;
import com.crediflow.application.feign.CreditClient;
import com.crediflow.application.mapper.LoanApplicationMapper;
import com.crediflow.application.service.LoanApplicationService;
import com.crediflow.common.api.credit.CreditResultResponse;
import com.crediflow.common.api.credit.LoanReviewEnqueueRequest;
import com.crediflow.common.api.credit.LoanRiskEvaluateRequest;
import java.util.Map;
import com.crediflow.common.api.user.UserEligibilityResponse;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

import com.crediflow.application.feign.UserClient;
import com.crediflow.application.feign.ContractClient;

@Service
public class LoanApplicationServiceImpl extends ServiceImpl<LoanApplicationMapper, LoanApplication> implements LoanApplicationService {

    @Autowired
    private CreditClient creditClient;

    @Autowired
    private UserClient userClient;
    
    @Autowired
    private ContractClient contractClient;

    @Override
    @com.crediflow.common.annotation.Idempotent(key = "'LOAN_APP:' + #idmpToken")
    public LoanApplication applyLoan(Long userId, BigDecimal applyAmount, Integer term, String idmpToken) {
        // 0.0 校验分期期数是否在白名单内
        if (term == null || (term != 3 && term != 6 && term != 12)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不支持的分期期数，仅支持 3, 6, 12 期");
        }

        // 0. KYC v2 + 主卡校验（OpenSpec: kyc-realname-face-bankcard-rebuild）
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

        // 2. 联合校验：检查有效授信额度
        Result<CreditResultResponse> creditResult = creditClient.getActiveCreditInternal(userId);
        if (creditResult == null || creditResult.getCode() != 200 || creditResult.getData() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无有效授信，请先申请额度");
        }

        CreditResultResponse creditData = creditResult.getData();
        BigDecimal creditAmount = creditData.getCreditAmount();
        BigDecimal usedAmount = creditData.getUsedAmount();
        BigDecimal availableAmount = creditAmount.subtract(usedAmount);

        if (applyAmount.compareTo(availableAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请金额超过可用额度");
        }

        // 2.5 授信合同前置：必须已经签订授信合同
        Result<Map<String, Object>> contractResult = contractClient.getCreditContractStatus(userId);
        if (contractResult == null || contractResult.getCode() != 200 || contractResult.getData() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法获取授信合同状态");
        }
        
        String contractStatus = (String) contractResult.getData().get("status");
        if (!"SIGNED".equals(contractStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "必须先签署授信协议才能借款");
        }

        // 3. 状态机：创建申请单 (INIT -> 评估)
        LoanApplication application = new LoanApplication();
        application.setApplicationNo("LOAN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        application.setUserId(userId);
        application.setApplyAmount(applyAmount);
        application.setTerm(term);
        application.setStatus("PENDING_RISK");
        application.setCreatedAt(new Date());
        application.setUpdatedAt(new Date());
        this.save(application);

        // 4. 同步调用风控评估
        LoanRiskEvaluateRequest evalReq = new LoanRiskEvaluateRequest();
        evalReq.setUserId(userId);
        evalReq.setApplicationId(application.getId());
        evalReq.setApplyAmount(applyAmount);

        Result<String> evalResult = creditClient.evaluateLoanRisk(evalReq);
        if (evalResult == null || evalResult.getCode() != 200) {
            application.setStatus("REJECTED"); // 风控拦截或异常直接拒绝
            this.updateById(application);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, evalResult != null ? evalResult.getMessage() : "风控评估失败");
        }
        
        String riskLevel = evalResult.getData(); // LOW, MEDIUM, HIGH, MANUAL_REVIEW, REJECTED
        application.setRiskLevel(riskLevel);
        if ("REJECTED".equals(riskLevel)) {
            application.setStatus("REJECTED");
            this.updateById(application);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "风控拦截，暂不符合借款条件");
        } else if ("MANUAL_REVIEW".equals(riskLevel)) {
            application.setStatus("PENDING_MANUAL_REVIEW");
            this.updateById(application);
            // 触发入队进行人工审核
            LoanReviewEnqueueRequest enqueueReq = new LoanReviewEnqueueRequest();
            enqueueReq.setApplicationId(application.getId());
            enqueueReq.setUserId(userId);
            enqueueReq.setSceneType("LOAN");
            creditClient.enqueueLoanReview(enqueueReq);
            return application;
        } else if ("LOW".equals(riskLevel)) {
            // 低风险，直接进入合同处理
            application.setStatus("CONTRACT_PROCESSING");
            this.updateById(application);
            // 触发放款流程
            this.approve(application.getId());
        } else if ("MEDIUM".equals(riskLevel) || "HIGH".equals(riskLevel)) {
            // 中风险，进入人脸识别环节
            application.setStatus("PENDING_FACE");
            this.updateById(application);
        }

        return application;
    }

    @Autowired
    private com.crediflow.application.mapper.LocalMessageMapper localMessageMapper;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void approve(Long applicationId) {
        LoanApplication application = this.getById(applicationId);
        if (!"PENDING".equals(application.getStatus()) && !"PENDING_RISK".equals(application.getStatus()) && !"PENDING_MANUAL_REVIEW".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请单状态非法，无法通过");
        }
        // 根据规范，状态置为“处理中（生成合同阶段）”
        application.setStatus("CONTRACT_PROCESSING");
        this.updateById(application);
        
        com.crediflow.common.event.LoanLifecycleMessage message = new com.crediflow.common.event.LoanLifecycleMessage();
        message.setLoanApplicationId(application.getId());
        message.setUserId(application.getUserId());
        message.setEventType(com.crediflow.common.event.MqConstants.TAG_LOAN_APPROVED);
        
        java.util.Map<String, Object> payloadMap = new java.util.HashMap<>();
        payloadMap.put("applyAmount", application.getApplyAmount());
        payloadMap.put("term", application.getTerm());
        message.setPayload(payloadMap);
        
        try {
            com.crediflow.application.entity.LocalMessage localMsg = new com.crediflow.application.entity.LocalMessage();
            localMsg.setTopic(com.crediflow.common.event.MqConstants.TOPIC_LOAN_LIFECYCLE);
            localMsg.setTag(com.crediflow.common.event.MqConstants.TAG_LOAN_APPROVED);
            localMsg.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(message));
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
}
