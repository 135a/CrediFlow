package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 内部信贷控制器类
 * 提供微服务间调用的REST API接口，受内网签名隔离保护
 */
@RestController
@RequestMapping("/api/internal/credit")
public class CreditInternalController {

    @Autowired
    private CreditService creditService;
    
    @Autowired
    private com.crediflow.credit.service.CreditApplicationService creditApplicationService;

    @GetMapping("/active")
    public Result<CreditResult> getActiveCreditInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getActiveCredit(userId));
    }

    @PostMapping("/apply")
    public Result<java.util.Map<String, Object>> applyCreditInternal(@RequestParam("userId") Long userId) {
        com.crediflow.credit.entity.CreditApplication app = creditService.applyCredit(userId);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("applicationId", app.getId());
        map.put("status", app.getStatus());
        return Result.success(map);
    }

    @GetMapping("/status")
    public Result<java.util.Map<String, Object>> getCreditStatusInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditApplicationService.getLastApplicationStatus(userId));
    }
    
    @GetMapping("/quota")
    public Result<java.util.Map<String, Object>> getCreditQuotaInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getQuotaSummary(userId));
    }
    
    @GetMapping("/last-result")
    public Result<java.util.Map<String, Object>> getLastResultInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditApplicationService.getLastApplicationResult(userId));
    }
    
    @PostMapping("/risk-signal/escalate")
    public Result<Void> escalateRiskSignal(@RequestBody java.util.Map<String, Object> signalData) {
        creditService.escalateRiskSignal(signalData);
        return Result.success();
    }

    @PostMapping("/evaluate-loan")
    public Result<String> evaluateLoanRisk(@RequestBody java.util.Map<String, Object> req) {
        return Result.success(creditService.evaluateLoanRisk(req));
    }

    @PostMapping("/review/enqueue")
    public Result<Void> enqueueLoanReview(@RequestBody java.util.Map<String, Object> req) {
        creditService.enqueueLoanReview(req);
        return Result.success();
    }
    
    @PostMapping("/quota/deduct")
    public Result<Void> deductQuota(@RequestBody java.util.Map<String, Object> req) {
        Long userId = Long.valueOf(req.get("userId").toString());
        java.math.BigDecimal amount = new java.math.BigDecimal(req.get("amount").toString());
        creditService.deductQuota(userId, amount);
        return Result.success();
    }
}
