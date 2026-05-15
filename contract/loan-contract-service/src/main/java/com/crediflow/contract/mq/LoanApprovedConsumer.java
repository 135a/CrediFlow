package com.crediflow.contract.mq;

import com.crediflow.common.event.LoanLifecycleMessage;
import com.crediflow.common.event.MqConstants;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.contract.service.LoanContractService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 贷款批准事件消费者：仅幂等创建 INIT 预签合同，并在<strong>本次新建成功</strong>时通知下游「合同就绪」。
 * <p>
 * 不在此路径解析 payload 中的金额/期数，避免与 {@link LoanContractService#generateContract} 实际入参脱节；
 * 不在此路径注入授信客户端，以免读者误以为 MQ 会触发额度变更。
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_LOAN_LIFECYCLE, consumerGroup = "loan-contract-group", selectorExpression = MqConstants.TAG_LOAN_APPROVED)
public class LoanApprovedConsumer implements RocketMQListener<LoanLifecycleMessage> {

    @Autowired
    private LoanContractService loanContractService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(LoanLifecycleMessage message) {
        log.info("Received LOAN_APPROVED_EVENT: {}", message);

        try {
            validateMessage(message);

            // generateContract：已存在合同时返回 false，不向 MQ 下游重复发 CONTRACT_READY（与 MQ 重试语义对齐）
            boolean inserted = loanContractService.generateContract(
                    message.getLoanApplicationId(),
                    message.getUserId(),
                    "LOAN_CONTRACT");

            if (!inserted) {
                log.info("LOAN_APPROVED 幂等跳过：已存在合同，不发送 CONTRACT_READY，applicationId={} userId={}",
                        message.getLoanApplicationId(), message.getUserId());
                return;
            }

            LoanLifecycleMessage nextMsg = new LoanLifecycleMessage();
            nextMsg.setLoanApplicationId(message.getLoanApplicationId());
            nextMsg.setUserId(message.getUserId());
            nextMsg.setEventType(MqConstants.TAG_CONTRACT_READY);

            rocketMQTemplate.convertAndSend(MqConstants.TOPIC_LOAN_LIFECYCLE + ":" + MqConstants.TAG_CONTRACT_READY, nextMsg);
            log.info("已新建 INIT 合同并发送 CONTRACT_READY，applicationId={}", message.getLoanApplicationId());
        } catch (BusinessException e) {
            log.error("处理 LOAN_APPROVED 失败: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("处理 LOAN_APPROVED 发生未预期异常", e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to process LOAN_APPROVED_EVENT: " + detail);
        }
    }

    private static void validateMessage(LoanLifecycleMessage message) {
        if (message == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "LOAN_APPROVED 消息体为空");
        }
        Long applicationId = message.getLoanApplicationId();
        Long userId = message.getUserId();
        if (applicationId == null || applicationId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "LOAN_APPROVED 缺少有效 loanApplicationId: " + applicationId);
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "LOAN_APPROVED 缺少有效 userId: " + userId);
        }
    }
}
