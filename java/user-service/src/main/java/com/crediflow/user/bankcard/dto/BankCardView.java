package com.crediflow.user.bankcard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 对外卡片视图：脱敏。
 */
@Data
@AllArgsConstructor
public class BankCardView {
    private String bindCardId;
    private String bankCode;
    private String cardNoMask;
    private String reservedPhoneMask;
    /** VERIFIED / UNBOUND / FAILED */
    private String status;
    private boolean primary;
}
