package com.crediflow.user.dto;

import java.util.Map;

/** step2 成功响应（可序列化为 Feign Map） */
public record UserKycStep2Response(String idCardMask, String realnameStatus, String providerTxnNo) {

    public Map<String, Object> toMap() {
        return Map.of(
                "idCardMask", idCardMask == null ? "" : idCardMask,
                "realnameStatus", realnameStatus == null ? "" : realnameStatus,
                "providerTxnNo", providerTxnNo == null ? "" : providerTxnNo);
    }
}
