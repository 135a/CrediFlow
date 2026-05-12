package com.crediflow.contract.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.contract.entity.LoanContract;

public interface LoanContractService extends IService<LoanContract> {
    void generateContract(Long applicationId, Long userId);
}
