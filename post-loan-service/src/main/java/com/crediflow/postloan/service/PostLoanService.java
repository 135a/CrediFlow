package com.crediflow.postloan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.postloan.entity.CollectionTask;

import java.math.BigDecimal;

public interface PostLoanService extends IService<CollectionTask> {
    void processOverdue(Long planId, Long contractId, Long userId, Integer overdueDays, BigDecimal principal);
}
