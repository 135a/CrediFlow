package com.crediflow.user.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * KYC v2 step2 同步受理响应。
 */
@Data
@AllArgsConstructor
public class KycStep2Response {
    /** PROCESSING / VERIFIED */
    private String faceStatus;
    private String providerBizNo;
    /** MOCK / WHITELIST / HTTP */
    private String channel;
    private boolean kycPassed;
}
