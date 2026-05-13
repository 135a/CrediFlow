package com.crediflow.common.event;

import lombok.Data;

/**
 * KYC v2 通过事件：实名 + 实人均为 VERIFIED 后投递。
 */
@Data
public class KycPassedEvent {
    /** 固定为 {@link MqConstants#TAG_KYC_PASSED}，便于消费方路由。 */
    private String eventType;
    private Long userId;
    private String realnameProviderTxnNo;
    private String faceProviderTxnNo;
    /** ISO-8601 字符串，UTC 或带偏移由生产者约定。 */
    private String passedAt;
    private String idCardMask;
}
