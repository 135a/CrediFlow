package com.crediflow.repayment.mq;

import com.crediflow.common.event.LoanLifecycleMessage;
import com.crediflow.common.event.MqConstants;
import com.crediflow.repayment.service.RepaymentPlanService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_LOAN_LIFECYCLE, consumerGroup = "repayment-plan-group", selectorExpression = MqConstants.TAG_FUND_DISBURSED)
public class FundDisbursedConsumer implements RocketMQListener<LoanLifecycleMessage> {

    @Autowired
    private RepaymentPlanService repaymentPlanService;

    @Override
    public void onMessage(LoanLifecycleMessage message) {
        log.info("Received FUND_DISBURSED_EVENT: {}", message);
        
        try {
            // 生成还款计划
            repaymentPlanService.generateRepaymentPlan(message.getLoanApplicationId(), message.getUserId());
            log.info("Repayment plan generated for LoanApplicationId: {}", message.getLoanApplicationId());
        } catch (Exception e) {
            log.error("Failed to process FUND_DISBURSED_EVENT", e);
            throw e;
        }
    }
}
