package com.crediflow.user.bankcard.dto;

import lombok.Data;

@Data
public class BankCardBindRequest {
    private String cardNo;
    private String bankCode;
    private String reservedPhone;
}
