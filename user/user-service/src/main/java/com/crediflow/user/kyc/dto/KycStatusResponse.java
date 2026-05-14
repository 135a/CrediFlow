package com.crediflow.user.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * KYC 综合状态查询响应。
 */
@Data
@AllArgsConstructor
public class KycStatusResponse {
    private String eligibilityStatus;
    private String realnameStatus;
    private String faceStatus;
    private boolean kycPassed;
    private String idCardMask;
}
