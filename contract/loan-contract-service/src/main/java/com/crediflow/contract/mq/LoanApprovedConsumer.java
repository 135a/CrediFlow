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

/**
 * 贷款批准事件消费者
 * 用于处理贷款审批通过后的相关业务逻辑，包括生成预签合同和发送合同就绪事件
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_LOAN_LIFECYCLE, consumerGroup = "loan-contract-group", selectorExpression = MqConstants.TAG_LOAN_APPROVED)
public class LoanApprovedConsumer implements RocketMQListener<LoanLifecycleMessage> {

    /**
     * 贷款合同服务接口
     * 用于处理合同相关的业务逻辑
     */
    @Autowired
    private LoanContractService loanContractService;

    /**
     * RocketMQ模板
     * 用于发送消息到消息队列
     */
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 信用服务客户端
     * 用于调用信用相关的服务接口
     */
    @Autowired
    private com.crediflow.contract.feign.CreditClient creditClient;

    /**
     * 消息处理方法
     * 当接收到贷款批准事件时，执行以下操作：
     * 1. 解析消息内容，获取贷款金额和期限
     * 2. 生成预签合同（状态为INIT）
     * 3. 发送合同就绪事件
     *
     * @param message 贷款生命周期消息对象，包含贷款申请相关信息
     */
    @Override
    public void onMessage(LoanLifecycleMessage message) {
        // 记录接收到的贷款批准事件日志
        log.info("Received LOAN_APPROVED_EVENT: {}", message);
        
        try {
            // 解析消息中的贷款申请金额和期限
            java.util.Map<String, Object> payload = (java.util.Map<String, Object>) message.getPayload();
            java.math.BigDecimal amount = new java.math.BigDecimal(payload.get("applyAmount").toString());
            Integer term = Integer.valueOf(payload.get("term").toString());

            // 1. 生成预签合同（状态 INIT）
            loanContractService.generateContract(message.getLoanApplicationId(), message.getUserId(), "LOAN_CONTRACT");
            
            // 抛出合同就绪事件
            LoanLifecycleMessage nextMsg = new LoanLifecycleMessage();
            nextMsg.setLoanApplicationId(message.getLoanApplicationId());
            nextMsg.setUserId(message.getUserId());
            nextMsg.setEventType(MqConstants.TAG_CONTRACT_READY);
            
            rocketMQTemplate.convertAndSend(MqConstants.TOPIC_LOAN_LIFECYCLE + ":" + MqConstants.TAG_CONTRACT_READY, nextMsg);
            log.info("Contract generated and CONTRACT_READY_EVENT sent for LoanApplicationId: {}", message.getLoanApplicationId());
        } catch (Exception e) {
            log.error("Failed to process LOAN_APPROVED_EVENT", e);
            throw new com.crediflow.common.exception.BusinessException(com.crediflow.common.exception.ErrorCode.BUSINESS_ERROR, "Failed to process LOAN_APPROVED_EVENT");
        }
    }
}
