package com.crediflow.user.messaging;

import com.crediflow.common.messaging.DomainEventEnvelope;
import com.crediflow.common.messaging.EventTopicNames;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * 事务提交后发送放款事件（任务 4.2 骨架：事务后发送）。
 */
@Component
public class LoanDisbursedEventPublisher {

    private static final int SCHEMA_VERSION = 1;

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoanDisbursedEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void publishAfterCommit(String agreementId, long amountMinor) {
        DomainEventEnvelope envelope = DomainEventEnvelope.create(
                "LoanDisbursed",
                SCHEMA_VERSION,
                Map.of("agreementId", agreementId, "amountMinor", amountMinor)
        );
        Runnable send = () -> {
            try {
                String json = objectMapper.writeValueAsString(envelope);
                rocketMQTemplate.syncSend(
                        EventTopicNames.TOPIC_LOAN_DISBURSED,
                        MessageBuilder.withPayload(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)).build()
                );
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
