package com.crediflow.repayment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.repayment.entity.RepaymentPlan;
import com.crediflow.repayment.mapper.RepaymentPlanMapper;
import com.crediflow.repayment.service.RepaymentPlanService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

@Service
public class RepaymentPlanServiceImpl extends ServiceImpl<RepaymentPlanMapper, RepaymentPlan> implements RepaymentPlanService {

    @Override
    public void generateRepaymentPlan(Long applicationId, Long userId) {
        // Mock generation for 12 months, 10000 total principal, 0.05 interest
        int totalTerms = 12;
        BigDecimal totalPrincipal = new BigDecimal("10000.00");
        BigDecimal principalPerTerm = totalPrincipal.divide(new BigDecimal(totalTerms), 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal interestPerTerm = principalPerTerm.multiply(new BigDecimal("0.05")).setScale(2, BigDecimal.ROUND_HALF_UP);
        
        Calendar cal = Calendar.getInstance();
        
        for (int i = 1; i <= totalTerms; i++) {
            cal.add(Calendar.MONTH, 1);
            
            RepaymentPlan plan = new RepaymentPlan();
            plan.setApplicationId(applicationId);
            plan.setUserId(userId);
            plan.setTermIndex(i);
            plan.setTotalTerms(totalTerms);
            plan.setPrincipalAmount(principalPerTerm);
            plan.setInterestAmount(interestPerTerm);
            plan.setPenaltyAmount(BigDecimal.ZERO);
            plan.setTotalAmount(principalPerTerm.add(interestPerTerm));
            plan.setDueDate(cal.getTime());
            plan.setStatus("PENDING");
            plan.setCreatedAt(new Date());
            plan.setUpdatedAt(new Date());
            
            this.save(plan);
        }
    }

    @Override
    public void processRepayment(Long planId, Long userId) {
        RepaymentPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "还款计划不存在或无权限");
        }
        if (!"PENDING".equals(plan.getStatus()) && !"OVERDUE".equals(plan.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "还款计划状态非法，无法还款");
        }
        
        // Mock payment via gateway
        plan.setStatus("PAID");
        plan.setUpdatedAt(new Date());
        this.updateById(plan);
    }
}
