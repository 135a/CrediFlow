package com.crediflow.fund.mq;

import com.crediflow.common.event.LoanLifecycleMessage;
import com.crediflow.common.event.MqConstants;
import com.crediflow.fund.service.FundFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_LOAN_LIFECYCLE, consumerGroup = "fund-flow-group", selectorExpression = MqConstants.TAG_CONTRACT_READY)
public class ContractReadyConsumer implements RocketMQListener<LoanLifecycleMessage> {

    @Autowired
    private FundFlowService fundFlowService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(LoanLifecycleMessage message) {
        log.info("Received CONTRACT_READY_EVENT: {}", message);
        
        try {
            boolean emitFundDisbursed = fundFlowService.processDisbursement(message);

            if (emitFundDisbursed) {
                LoanLifecycleMessage nextMsg = new LoanLifecycleMessage();
                nextMsg.setLoanApplicationId(message.getLoanApplicationId());
                nextMsg.setUserId(message.getUserId());
                nextMsg.setEventType(MqConstants.TAG_FUND_DISBURSED);

                rocketMQTemplate.convertAndSend(MqConstants.TOPIC_LOAN_LIFECYCLE + ":" + MqConstants.TAG_FUND_DISBURSED, nextMsg);
                log.info("Disbursement successful and FUND_DISBURSED_EVENT sent for LoanApplicationId: {}", message.getLoanApplicationId());
            } else {
                log.info("Skipping immediate FUND_DISBURSED_EVENT for LoanApplicationId: {} (gateway path or compatibility flag)", message.getLoanApplicationId());
            }
        } catch (Exception e) {
            log.error("Failed to process CONTRACT_READY_EVENT", e);
            throw e;
        }
    }
}
