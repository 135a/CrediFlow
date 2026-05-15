package com.crediflow.common.api.credit;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanRiskEvaluateRequest {
    private Long userId;
    private Long applicationId;
    private BigDecimal applyAmount;
}
