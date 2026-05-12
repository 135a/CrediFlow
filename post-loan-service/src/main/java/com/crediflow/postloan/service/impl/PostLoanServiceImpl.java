package com.crediflow.postloan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.postloan.entity.CollectionTask;
import com.crediflow.postloan.mapper.CollectionTaskMapper;
import com.crediflow.postloan.service.PostLoanService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PostLoanServiceImpl extends ServiceImpl<CollectionTaskMapper, CollectionTask> implements PostLoanService {

    @org.springframework.beans.factory.annotation.Value("${crediflow.post-loan.penalty-rate:0.0005}")
    private String penaltyRateStr;

    @Override
    public void processOverdue(Long planId, Long contractId, Long userId, Integer overdueDays, BigDecimal principal) {
        // 1. 计算罚息：本金 * 逾期天数 * 配置的日利率
        BigDecimal penaltyRate = new BigDecimal(penaltyRateStr);
        BigDecimal penalty = principal.multiply(new BigDecimal(overdueDays))
                .multiply(penaltyRate)
                .setScale(2, RoundingMode.HALF_UP);
        
        log.info("OVERDUE_PROCESS: Plan {} overdue for {} days. Calculated penalty: {}", planId, overdueDays, penalty);
        // 此处应调用 repayment-service 更新对应 plan 的罚息和状态

        // 2. 生成催收任务 (如果逾期超过 3 天)
        if (overdueDays >= 3) {
            CollectionTask task = new CollectionTask();
            task.setContractId(contractId);
            task.setPlanId(planId);
            task.setUserId(userId);
            task.setStatus("INIT");
            task.setMethod(overdueDays > 7 ? "PHONE" : "SMS");
            task.setCreatedAt(new Date());
            task.setUpdatedAt(new Date());
            this.save(task);
            log.info("COLLECTION_TASK: Created {} collection task for user {}", task.getMethod(), userId);
        }
    }
}
