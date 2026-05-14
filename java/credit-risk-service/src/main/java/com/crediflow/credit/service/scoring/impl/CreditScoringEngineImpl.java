package com.crediflow.credit.service.scoring.impl;

import com.crediflow.credit.service.scoring.CreditScoringEngine;
import com.crediflow.credit.service.scoring.ScoreDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CreditScoringEngineImpl implements CreditScoringEngine {

    @Value("${crediflow.credit.threshold.low:80}")
    private double lowRiskThreshold;

    @Value("${crediflow.credit.threshold.high:50}")
    private double highRiskThreshold;

    @Value("${crediflow.credit.scoring.version:v2.0}")
    private String scoringVersion;

    @Override
    public ScoreDetail calculateScore(Long userId) {
        log.info("Calculating credit score for user: {}", userId);
        
        // 1.2 实现 S1~S4 计算器 (Mock data for now, TODO: Fetch real external credit data)
        int s1 = fetchS1(userId);
        int s2 = fetchS2(userId);
        int s3 = fetchS3(userId);
        int s4 = fetchS4(userId);
        
        // 1.3 WeightedScoreEngine
        // TotalScore = 0.2*S1 + 0.4*S2 + 0.2*S3 + 0.2*S4
        double totalScore = 0.2 * s1 + 0.4 * s2 + 0.2 * s3 + 0.2 * s4;
        
        // RiskLevelClassifier
        String riskLevel;
        if (totalScore >= lowRiskThreshold) {
            riskLevel = "LOW";
        } else if (totalScore < highRiskThreshold) {
            riskLevel = "HIGH";
        } else {
            riskLevel = "MEDIUM";
        }
        
        ScoreDetail detail = new ScoreDetail();
        detail.setS1Score(s1);
        detail.setS2Score(s2);
        detail.setS3Score(s3);
        detail.setS4Score(s4);
        detail.setTotalScore(totalScore);
        detail.setRiskLevel(riskLevel);
        detail.setRulesVersion(scoringVersion);
        
        log.info("User {} score calculated: Total={}, Level={}", userId, totalScore, riskLevel);
        return detail;
    }
    
    private int fetchS1(Long userId) { return 85; } // Mock S1
    private int fetchS2(Long userId) { return 70; } // Mock S2
    private int fetchS3(Long userId) { return 90; } // Mock S3
    private int fetchS4(Long userId) { return 80; } // Mock S4
}
