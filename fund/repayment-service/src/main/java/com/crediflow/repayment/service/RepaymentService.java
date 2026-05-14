package com.crediflow.repayment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.repayment.entity.RepaymentPlan;

import java.math.BigDecimal;
import java.util.List;

public interface RepaymentService extends IService<RepaymentPlan> {
    List<RepaymentPlan> generatePlans(Long userId, Long contractId, BigDecimal loanAmount, BigDecimal interestRate, Integer term);
    RepaymentPlan activeRepay(Long userId, Long planId, String idmpToken);
}
