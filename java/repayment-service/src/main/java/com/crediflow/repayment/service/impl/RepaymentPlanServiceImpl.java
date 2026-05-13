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
            plan.setPeriod(i);
            plan.setTotalTerms(totalTerms);
            plan.setPrincipal(principalPerTerm);
            plan.setInterest(interestPerTerm);
            plan.setPenalty(BigDecimal.ZERO);
            plan.setTotalAmount(principalPerTerm.add(interestPerTerm));
            plan.setDueDate(cal.getTime());
            plan.setStatus("PENDING");
            plan.setCreatedAt(new Date());
            plan.setUpdatedAt(new Date());
            
            this.save(plan);
        }
    }

    /**
     * 旧的 Mock「第三方收银网关」直连路径，按 fund-provider-go-gateway 任务 8.2 已禁用：
     * 所有真实资金外呼必须经由 Go {@code fund-channel-gateway}。保留方法签名仅为
     * 兼容尚未迁移的内部调用，调用时立即抛出禁用异常，提示改用 {@code RepaymentService.activeRepay}。
     */
    @Override
    @Deprecated
    public void processRepayment(Long planId, Long userId) {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                "Legacy mock repayment path is disabled; call RepaymentService#activeRepay via fund-channel-gateway");
    }
}
