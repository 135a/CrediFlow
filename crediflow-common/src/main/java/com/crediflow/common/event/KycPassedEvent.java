package com.crediflow.common.event;

import lombok.Data;

/**
 * KYC v2 通过事件：实名 + 实人均为 VERIFIED 后投递。
 * 该类用于表示用户KYC认证通过的事件信息。
 */
@Data  // Lombok注解，自动生成getter、setter等方法
public class KycPassedEvent {
    /** 固定为 {@link MqConstants#TAG_KYC_PASSED}，便于消费方路由。 */
    // 事件类型，用于标识不同类型的事件
    private String eventType;
    // 用户ID，用于标识具体的用户
    private Long userId;
    // 真实姓名提供商交易号，用于关联真实姓名验证的交易
    private String realnameProviderTxnNo;
    // 人脸识别提供商交易号，用于关联人脸识别验证的交易
    private String faceProviderTxnNo;
    /** ISO-8601 字符串，UTC 或带偏移由生产者约定。 */
    private String passedAt;
    private String idCardMask;
}
