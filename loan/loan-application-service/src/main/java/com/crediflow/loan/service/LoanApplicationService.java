package com.crediflow.loan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.loan.entity.LoanApplication;

import java.math.BigDecimal;

public interface LoanApplicationService extends IService<LoanApplication> {
    LoanApplication applyLoan(Long userId, BigDecimal applyAmount, Integer term, String idempotencyToken);
    void approve(Long applicationId);
    void reject(Long applicationId, String reason);
    
    com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> listApplications(Integer page, Integer size, String status);
    com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> listAdminApplications(long current, long size, java.util.Date startTime, java.util.Date endTime, String phone);
    java.util.Map<String, Object> getApplicationMap(Long id);
    void manualReview(Long id, String action, String reason, Long reviewerId);
}
