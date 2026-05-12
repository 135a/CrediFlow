package com.crediflow.contract.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.contract.entity.LoanContract;

import java.math.BigDecimal;
import java.util.Map;

public interface LoanContractService extends IService<LoanContract> {
    Map<String, Object> signAndGenerateContract(Long userId, Long applicationId, BigDecimal amount, Integer term, boolean agreed);
    Map<String, Object> getContractLink(Long userId, Long applicationId);
}
