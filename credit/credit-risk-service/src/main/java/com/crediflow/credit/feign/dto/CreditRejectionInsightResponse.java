package com.crediflow.credit.feign.dto;

import lombok.Data;

@Data
public class CreditRejectionInsightResponse {
    private String userSafeInsight;
    private String adminInsight;
    private String actionableAdvice;
}
