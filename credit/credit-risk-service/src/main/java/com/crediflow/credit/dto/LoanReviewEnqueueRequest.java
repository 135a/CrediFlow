package com.crediflow.credit.dto;

import lombok.Data;

@Data
public class LoanReviewEnqueueRequest {
    private Long applicationId;
    private Long userId;
    /** 场景类型，默认 LOAN */
    private String sceneType = "LOAN";
}
