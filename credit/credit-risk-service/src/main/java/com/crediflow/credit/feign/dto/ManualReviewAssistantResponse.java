package com.crediflow.credit.feign.dto;

import lombok.Data;

import java.util.List;

@Data
public class ManualReviewAssistantResponse {
    private List<String> riskDetails;
    private Double defaultProbability;
    private Double fraudProbability;
    private String suggestion;
}
