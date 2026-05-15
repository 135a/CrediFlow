package com.crediflow.credit.feign.dto;

import lombok.Data;

@Data
public class ManualReviewAssistantRequest {
    private Long userId;
    private String sceneType;
    private ManualReviewScoreDetail scoreDetail;
}
