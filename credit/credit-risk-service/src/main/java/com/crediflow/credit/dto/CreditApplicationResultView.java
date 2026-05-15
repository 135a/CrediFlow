package com.crediflow.credit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditApplicationResultView {
    private String status;
    private String auditReason;
    private String userSafeInsight;
}
