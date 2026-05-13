package com.crediflow.user.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * KYC v2 step1（二要素 + 闸门）对外响应。仅含脱敏字段与状态。
 */
@Data
@AllArgsConstructor
public class KycStep1Response {
    private String idCardMask;
    /** NOT_SUBMITTED / PROCESSING / VERIFIED / FAILED */
    private String realnameStatus;
    private String realnameProviderTxnNo;
    /** PASS / REJECTED_* */
    private String eligibilityStatus;
}
