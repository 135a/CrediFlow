package com.crediflow.credit.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewScoreDetail {
    private double totalScore;
    private String riskLevel;
}
