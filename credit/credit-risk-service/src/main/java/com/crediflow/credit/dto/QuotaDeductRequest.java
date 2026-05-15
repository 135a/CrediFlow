package com.crediflow.credit.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotaDeductRequest {
    private Long userId;
    private BigDecimal amount;
}
