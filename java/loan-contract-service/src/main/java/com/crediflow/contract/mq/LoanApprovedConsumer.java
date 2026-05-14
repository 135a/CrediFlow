package com.crediflow.contract.mq;

import com.crediflow.common.event.LoanLifecycleMessage;
import com.crediflow.common.event.MqConstants;
import com.crediflow.contract.service.LoanContractService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_LOAN_LIFECYCLE, consumerGroup = "loan-contract-group", selectorExpression = MqConstants.TAG_LOAN_APPROVED)
public class LoanApprovedConsumer implements RocketMQListener<LoanLifecycleMessage> {

    @Autowired
    private LoanContractService loanContractService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private com.crediflow.contract.feign.CreditClient creditClient;

    @Override
    public void onMessage(LoanLifecycleMessage message) {
        log.info("Received LOAN_APPROVED_EVENT: {}", message);
        
        try {
            java.util.Map<String, Object> payload = (java.util.Map<String, Object>) message.getPayload();
            java.math.BigDecimal amount = new java.math.BigDecimal(payload.get("applyAmount").toString());
            Integer term = Integer.valueOf(payload.get("term").toString());

            // 1. 生成合同
            loanContractService.generateContract(message.getLoanApplicationId(), message.getUserId(), "LOAN_CONTRACT");
            
            // 2. 生成借据与还款计划
            loanContractService.generateReceiptAndPlan(message.getLoanApplicationId(), message.getUserId(), amount, term);
            
            // 3. 扣减额度
            java.util.Map<String, Object> req = new java.util.HashMap<>();
            req.put("userId", message.getUserId());
            req.put("amount", amount);
            com.crediflow.common.web.Result<Void> deductResult = creditClient.deductQuota(req);
            if (deductResult == null || deductResult.getCode() != 200) {
                throw new RuntimeException("Deduct quota failed: " + (deductResult != null ? deductResult.getMessage() : "unknown"));
            }
            
            // 抛出合同就绪事件
            LoanLifecycleMessage nextMsg = new LoanLifecycleMessage();
            nextMsg.setLoanApplicationId(message.getLoanApplicationId());
            nextMsg.setUserId(message.getUserId());
            nextMsg.setEventType(MqConstants.TAG_CONTRACT_READY);
            
            rocketMQTemplate.convertAndSend(MqConstants.TOPIC_LOAN_LIFECYCLE + ":" + MqConstants.TAG_CONTRACT_READY, nextMsg);
            log.info("Contract generated and CONTRACT_READY_EVENT sent for LoanApplicationId: {}", message.getLoanApplicationId());
        } catch (Exception e) {
            log.error("Failed to process LOAN_APPROVED_EVENT", e);
            throw new RuntimeException(e); // 抛出异常让 MQ 重新投递
        }
    }
}
