package com.crediflow.common.event;

import lombok.Data;
import java.util.List;

/**
 * 对话意图风控升级事件
 */
@Data
public class ChatRiskEscalationEvent {
    /** 固定为 MqConstants.TAG_CHAT_RISK_ESCALATION */
    private String eventType;
    private Long userId;
    private String conversationId;
    
    /** 意图标签集，如 CASH_OUT_INTENT, NO_REPAYMENT_INTENT */
    private List<String> intentTags;
    
    /** 严重程度：LOW, MEDIUM, HIGH, CRITICAL */
    private String severity;
    
    /** 证据摘要（脱敏后） */
    private String evidenceSummary;
    
    /** 相关聊天记录原话，供后台审核员查看 */
    private List<String> relevantChatLogs;
    
    /** Agent对审核员的具体处置建议 */
    private String agentSuggestions;
    
    private String createdAt;
}
