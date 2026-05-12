package com.crediflow.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.application.entity.LoanApplication;

import java.math.BigDecimal;

public interface LoanApplicationService extends IService<LoanApplication> {
    LoanApplication applyLoan(Long userId, BigDecimal applyAmount, Integer term, String idmpToken);
    void approve(Long applicationId);
    void reject(Long applicationId, String reason);
}
