package com.crediflow.postloan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.postloan.entity.CollectionTask;
import com.crediflow.postloan.mapper.CollectionTaskMapper;
import com.crediflow.postloan.service.PostLoanService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

@Service
public class PostLoanServiceImpl extends ServiceImpl<CollectionTaskMapper, CollectionTask> implements PostLoanService {

    @Override
    public void processOverdue(Long planId, Long contractId, Long userId, Integer overdueDays, BigDecimal principal) {
        // 1. 计算罚息：本金 * 逾期天数 * 0.0005 (日息万分之五)
        BigDecimal penalty = principal.multiply(new BigDecimal(overdueDays))
                .multiply(new BigDecimal("0.0005"))
                .setScale(2, RoundingMode.HALF_UP);
        
        System.out.println("OVERDUE_PROCESS: Plan " + planId + " overdue for " + overdueDays + " days. Calculated penalty: " + penalty);
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
            System.out.println("COLLECTION_TASK: Created " + task.getMethod() + " collection task for user " + userId);
        }
    }
}
