package com.crediflow.application.controller;

import com.crediflow.application.entity.LoanApplication;
import com.crediflow.application.service.LoanApplicationService;
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
                                             @RequestParam("idmpToken") String idmpToken) {
        return Result.success(loanApplicationService.applyLoan(userId, applyAmount, term, idmpToken));
    }
    @GetMapping("/internal/admin/list")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<LoanApplication>> listApplications(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<LoanApplication> pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LoanApplication> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            query.eq(LoanApplication::getStatus, status);
        }
        query.orderByDesc(LoanApplication::getCreatedAt);
        return Result.success(loanApplicationService.page(pageParam, query));
    }
    @GetMapping("/internal/admin/{id}")
    public Result<LoanApplication> getApplication(@PathVariable("id") Long id) {
        return Result.success(loanApplicationService.getById(id));
    }
    @PostMapping("/internal/admin/{id}/review")
    public Result<Void> manualReview(@PathVariable("id") Long id,
                                     @RequestParam("action") String action,
                                     @RequestParam("reason") String reason,
                                     @RequestParam("reviewerId") Long reviewerId) {
        LoanApplication application = loanApplicationService.getById(id);
        if (application == null) {
            throw new com.crediflow.common.exception.BusinessException(com.crediflow.common.exception.ErrorCode.BUSINESS_ERROR, "申请单不存在");
        }
        if (!"PENDING_MANUAL_REVIEW".equals(application.getStatus())) {
            throw new com.crediflow.common.exception.BusinessException(com.crediflow.common.exception.ErrorCode.BUSINESS_ERROR, "非待人工审核状态，无法操作");
        }
        
        application.setManualReviewerId(reviewerId);
        application.setManualReviewReason(reason);
        application.setManualReviewTime(new java.util.Date());
        application.setUpdatedAt(new java.util.Date());
        loanApplicationService.updateById(application);

        if ("APPROVE".equalsIgnoreCase(action)) {
            loanApplicationService.approve(id);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            loanApplicationService.reject(id, reason);
        } else {
            throw new com.crediflow.common.exception.BusinessException(com.crediflow.common.exception.ErrorCode.BUSINESS_ERROR, "未知的审核动作");
        }
        
        return Result.success();
    }
}
