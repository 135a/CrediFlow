package com.crediflow.credit.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 授信申请状态（与 cf_credit_application.status 列一致）。
 */
@Getter
@RequiredArgsConstructor
public enum CreditApplicationStatus {
    PENDING_HARD_RULES("PENDING_HARD_RULES"),
    PENDING_SCORING("PENDING_SCORING"),
    PENDING_ROUTING("PENDING_ROUTING"),
    PENDING_SECONDARY_FACE("PENDING_SECONDARY_FACE"),
    PENDING_MANUAL_REVIEW("PENDING_MANUAL_REVIEW"),
    CONTRACT_PENDING("CONTRACT_PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    COMPLETED("COMPLETED");

    @EnumValue
    private final String code;
}
