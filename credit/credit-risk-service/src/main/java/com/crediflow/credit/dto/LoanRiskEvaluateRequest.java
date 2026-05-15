package com.crediflow.credit.dto;

import lombok.Data;

@Data
public class LoanRiskEvaluateRequest {
    private Long userId;
    private Long applicationId;
}
