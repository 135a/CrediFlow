package com.crediflow.common.event;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 授信生命周期事件
 */
@Data
public class CreditLifecycleEvent {
    /** 对应 MqConstants.TAG_CREDIT_* */
    private String eventType;
    private Long userId;
    private String applicationId;
    private String status; // APPROVED, REJECTED, PENDING_MANUAL_REVIEW
    
    // 成功时携带
    private BigDecimal totalQuota;
    
    // 拒绝时携带
    private String rejectReasonCode;
    
    private String createdAt;
}
