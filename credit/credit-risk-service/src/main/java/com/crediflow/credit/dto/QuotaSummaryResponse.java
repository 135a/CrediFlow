package com.crediflow.credit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotaSummaryResponse {
    private BigDecimal totalAmount;
    private BigDecimal availableAmount;
    private BigDecimal usedAmount;
}
