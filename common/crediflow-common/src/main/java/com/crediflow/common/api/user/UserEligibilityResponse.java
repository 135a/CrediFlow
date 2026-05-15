package com.crediflow.common.api.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 内部受理前置：KYC 是否通过、是否已绑主卡（不含明文 PII）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEligibilityResponse {
    private boolean kycPassed;
    private boolean hasPrimaryBankCard;
}
