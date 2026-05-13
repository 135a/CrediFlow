package com.crediflow.repayment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.repayment.entity.RepaymentPlan;

public interface RepaymentPlanService extends IService<RepaymentPlan> {
    void generateRepaymentPlan(Long applicationId, Long userId);
    void processRepayment(Long planId, Long userId);
}
