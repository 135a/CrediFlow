package com.crediflow.credit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.credit.dto.LoanReviewEnqueueRequest;
import com.crediflow.common.api.credit.LoanRiskEvaluateRequest;
import com.crediflow.credit.dto.QuotaSummaryResponse;
import com.crediflow.credit.dto.RiskSignalEscalateRequest;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.entity.CreditReviewQueue;

import java.math.BigDecimal;

public interface CreditService extends IService<CreditResult> {

    CreditApplication applyCredit(Long userId);

    CreditResult getActiveCredit(Long userId);

    void manualApprove(Long applicationId, String remark);

    void generateUserQuota(Long userId, double totalScore);

    void deductQuota(Long userId, BigDecimal amount);

    QuotaSummaryResponse getQuotaSummary(Long userId);

    void escalateRiskSignal(RiskSignalEscalateRequest request);

    String evaluateLoanRisk(LoanRiskEvaluateRequest request);

    void enqueueLoanReview(LoanReviewEnqueueRequest request);

    Page<CreditReviewQueue> listReviewQueue(long current, long size);
}
