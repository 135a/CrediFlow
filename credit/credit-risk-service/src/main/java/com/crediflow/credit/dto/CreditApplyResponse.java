package com.crediflow.credit.dto;

import com.crediflow.credit.enums.CreditApplicationStatus;
import lombok.Data;

@Data
public class CreditApplyResponse {
    private Long applicationId;
    private CreditApplicationStatus status;
}
