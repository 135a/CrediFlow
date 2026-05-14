package com.crediflow.credit.service.scoring;

import lombok.Data;

@Data
public class ScoreDetail {
    private int s1Score;
    private int s2Score;
    private int s3Score;
    private int s4Score;
    private double totalScore;
    private String riskLevel; // LOW, MEDIUM, HIGH
    private String rulesVersion;
}
