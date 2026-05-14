package com.crediflow.contract.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.contract.entity.LoanContract;

public interface LoanContractService extends IService<LoanContract> {
    void generateContract(Long applicationId, Long userId, String contractType);
    java.util.Map<String, Object> signAndGenerateContract(Long userId, Long applicationId, java.math.BigDecimal amount, Integer term, boolean agreed);
    java.util.Map<String, Object> getContractLink(Long userId, Long applicationId);
    void generateReceiptAndPlan(Long applicationId, Long userId, java.math.BigDecimal amount, Integer term);
}
