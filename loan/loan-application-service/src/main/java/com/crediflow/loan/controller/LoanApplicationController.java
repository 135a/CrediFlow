package com.crediflow.loan.controller;

import com.crediflow.loan.entity.LoanApplication;
import com.crediflow.loan.service.LoanApplicationService;
import com.crediflow.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/app/loan-application")
public class LoanApplicationController {

    @Autowired
    private LoanApplicationService loanApplicationService;

    @PostMapping("/apply")
    public Result<LoanApplication> applyLoan(@RequestHeader("X-User-Id") Long userId,
                                             @RequestParam("applyAmount") BigDecimal applyAmount,
                                             @RequestParam("term") Integer term,
                                             @RequestParam("idmpToken") String idempotencyToken) {
        return Result.success(loanApplicationService.applyLoan(userId, applyAmount, term, idempotencyToken));
    }
    @GetMapping("/internal/admin/list")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>>> listApplications(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        return Result.success(loanApplicationService.listApplications(page, size, status));
    }

    @GetMapping("/internal/admin/{id}")
    public Result<java.util.Map<String, Object>> getApplication(@PathVariable("id") Long id) {
        return Result.success(loanApplicationService.getApplicationMap(id));
    }

    @PostMapping("/internal/admin/{id}/review")
    public Result<Void> manualReview(@PathVariable("id") Long id,
                                     @RequestParam("action") String action,
                                     @RequestParam("reason") String reason,
                                     @RequestParam("reviewerId") Long reviewerId) {
        loanApplicationService.manualReview(id, action, reason, reviewerId);
        return Result.success();
    }
}
