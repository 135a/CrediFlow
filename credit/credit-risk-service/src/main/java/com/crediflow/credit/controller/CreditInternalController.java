package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import com.crediflow.credit.dto.CreditApplyResponse;
import com.crediflow.credit.dto.CreditApplicationResultView;
import com.crediflow.credit.dto.CreditApplicationStatusView;
import com.crediflow.credit.dto.LoanReviewEnqueueRequest;
import com.crediflow.common.api.credit.LoanRiskEvaluateRequest;
import com.crediflow.credit.dto.QuotaDeductRequest;
import com.crediflow.credit.dto.QuotaSummaryResponse;
import com.crediflow.credit.dto.RiskSignalEscalateRequest;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.service.CreditApplicationService;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 内部信贷接口，仅供微服务间调用，受内网签名隔离保护。
 */
@RestController
@RequestMapping("/api/internal/credit")
public class CreditInternalController {

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditApplicationService creditApplicationService;

    @GetMapping("/active")
    public Result<CreditResult> getActiveCreditInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getActiveCredit(userId));
    }

    @PostMapping("/apply")
    public Result<CreditApplyResponse> applyCreditInternal(@RequestParam("userId") Long userId) {
        CreditApplication app = creditService.applyCredit(userId);
        CreditApplyResponse resp = new CreditApplyResponse();
        resp.setApplicationId(app.getId());
        resp.setStatus(app.getStatus());
        return Result.success(resp);
    }

    @GetMapping("/status")
    public Result<CreditApplicationStatusView> getCreditStatusInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditApplicationService.getLastApplicationStatus(userId));
    }

    @GetMapping("/quota")
    public Result<QuotaSummaryResponse> getCreditQuotaInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getQuotaSummary(userId));
    }

    @GetMapping("/last-result")
    public Result<CreditApplicationResultView> getLastResultInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditApplicationService.getLastApplicationResult(userId));
    }

    @PostMapping("/risk-signal/escalate")
    public Result<Void> escalateRiskSignal(@RequestBody RiskSignalEscalateRequest request) {
        creditService.escalateRiskSignal(request);
        return Result.success();
    }

    @PostMapping("/evaluate-loan")
    public Result<String> evaluateLoanRisk(@RequestBody LoanRiskEvaluateRequest request) {
        return Result.success(creditService.evaluateLoanRisk(request));
    }

    @PostMapping("/review/enqueue")
    public Result<Void> enqueueLoanReview(@RequestBody LoanReviewEnqueueRequest request) {
        creditService.enqueueLoanReview(request);
        return Result.success();
    }

    @PostMapping("/quota/deduct")
    public Result<Void> deductQuota(@RequestBody QuotaDeductRequest request) {
        creditService.deductQuota(request.getUserId(), request.getAmount());
        return Result.success();
    }
}
