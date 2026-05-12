package com.crediflow.repayment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.repayment.entity.RepaymentPlan;
import com.crediflow.repayment.mapper.RepaymentPlanMapper;
import com.crediflow.repayment.service.RepaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RepaymentServiceImpl extends ServiceImpl<RepaymentPlanMapper, RepaymentPlan> implements RepaymentService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public List<RepaymentPlan> generatePlans(Long userId, Long contractId, BigDecimal loanAmount, BigDecimal interestRate, Integer term) {
        // 等本算法，利息按实际天数计算（日利率）
        BigDecimal principalPerPeriod = loanAmount.divide(new BigDecimal(term), 2, RoundingMode.HALF_UP);

        List<RepaymentPlan> plans = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        Date lastDate = cal.getTime();
        
        for (int i = 1; i <= term; i++) {
            cal.add(Calendar.MONTH, 1);
            Date dueDate = cal.getTime();
            
            // 计算当期的实际天数
            long diffInMillies = Math.abs(dueDate.getTime() - lastDate.getTime());
            long daysInPeriod = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
            lastDate = dueDate;

            // 利息 = 本金 * 日利率 * 计息天数
            BigDecimal interestPerPeriod = loanAmount.multiply(interestRate).multiply(new BigDecimal(daysInPeriod)).setScale(2, RoundingMode.HALF_UP);

            RepaymentPlan plan = new RepaymentPlan();
            plan.setContractId(contractId);
            plan.setUserId(userId);
            plan.setPeriod(i);
            plan.setPrincipal(principalPerPeriod);
            plan.setInterest(interestPerPeriod);
            plan.setPenalty(BigDecimal.ZERO);
            plan.setStatus("PENDING");
            plan.setDueDate(dueDate);
            plan.setCreatedAt(new Date());
            plan.setUpdatedAt(new Date());
            plans.add(plan);
        }
        
        this.saveBatch(plans);
        return plans;
    }

    @Override
    public RepaymentPlan activeRepay(Long userId, Long planId, String idmpToken) {
        // 幂等校验
        String key = "IDMP:REPAY:" + idmpToken;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请勿重复提交还款");
        }

        RepaymentPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            redisTemplate.delete(key);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "还款计划不存在");
        }
        if ("PAID".equals(plan.getStatus())) {
            redisTemplate.delete(key);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该期已还清");
        }

        // 模拟调用三方支付，成功后修改状态
        plan.setStatus("PAID");
        plan.setPaidTime(new Date());
        this.updateById(plan);
        
        // 此处应发送 MQ 消息通知 fund-flow-service 记账
        System.out.println("MQ_SEND: Repayment success for plan " + planId + ", amount: " + plan.getPrincipal().add(plan.getInterest()));

        return plan;
    }
}
