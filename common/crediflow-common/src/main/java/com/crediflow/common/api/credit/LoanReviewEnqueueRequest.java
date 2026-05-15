package com.crediflow.common.api.credit;

import lombok.Data;

@Data
public class LoanReviewEnqueueRequest {
    private Long applicationId;
    private Long userId;
    private String sceneType = "LOAN";
}
