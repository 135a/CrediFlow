package com.crediflow.user.kyc.messaging;

import com.crediflow.common.event.KycPassedEvent;
import com.crediflow.common.event.MqConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * KYC v2 通过事件发布器：实名 + 实人均 VERIFIED 时投递。
 */
@Component
public class KycPassedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KycPassedEventPublisher.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    public KycPassedEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void publish(Long userId, String realnameTxn, String faceTxn, String idCardMask) {
        KycPassedEvent event = new KycPassedEvent();
        event.setEventType(MqConstants.TAG_KYC_PASSED);
        event.setUserId(userId);
        event.setRealnameProviderTxnNo(realnameTxn);
        event.setFaceProviderTxnNo(faceTxn);
        event.setPassedAt(Instant.now().toString());
        event.setIdCardMask(idCardMask);
        Runnable send = () -> {
            if (rocketMQTemplate == null) {
                log.warn("[kyc-passed] RocketMQTemplate unavailable, skip publish userId={}", userId);
                return;
            }
            try {
                String json = objectMapper.writeValueAsString(event);
                rocketMQTemplate.syncSend(
                        MqConstants.TOPIC_KYC_PASSED + ":" + MqConstants.TAG_KYC_PASSED,
                        MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("KycPassedEvent 序列化失败", e);
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
