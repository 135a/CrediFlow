package com.crediflow.credit.service.scoring.impl;

import com.crediflow.credit.service.scoring.CreditScoringEngine;
import com.crediflow.credit.service.scoring.ScoreDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 信用评分引擎实现类
 * 实现了信用评分和贷款评分的计算功能
 */
@Slf4j
@Service
public class CreditScoringEngineImpl implements CreditScoringEngine {

    /**
     * 低风险阈值配置
     * 默认值为80
     */
    @Value("${crediflow.credit.threshold.low:80}")
    private double lowRiskThreshold;

    /**
     * 高风险阈值配置
     * 默认值为50
     */
    @Value("${crediflow.credit.threshold.high:50}")
    private double highRiskThreshold;

    /**
     * 评分版本号配置
     * 默认值为v2.0
     */
    @Value("${crediflow.credit.scoring.version:v2.0}")
    private String scoringVersion;

    /**
     * 贷款低风险阈值配置
     * 默认值为85
     */
    @Value("${crediflow.loan.threshold.low:85}")
    private double loanLowRiskThreshold;

    /**
     * 贷款高风险阈值配置
     * 默认值为60
     */
    @Value("${crediflow.loan.threshold.high:60}")
    private double loanHighRiskThreshold;

    /**
     * 计算用户信用评分
     * @param userId 用户ID
     * @return 评分详情
     */
    @Override
    public ScoreDetail calculateScore(Long userId) {
        return doCalculate(userId, lowRiskThreshold, highRiskThreshold);
    }

    /**
     * 计算用户贷款评分
     * @param userId 用户ID
     * @return 评分详情
     */
    public ScoreDetail calculateLoanScore(Long userId) {
        return doCalculate(userId, loanLowRiskThreshold, loanHighRiskThreshold);
    }

    /**
     * 执行评分计算的核心方法
     * @param userId 用户ID
     * @param lowThresh 低风险阈值
     * @param highThresh 高风险阈值
     * @return 评分详情
     */
    private ScoreDetail doCalculate(Long userId, double lowThresh, double highThresh) {
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
        if (totalScore >= lowThresh) {
            riskLevel = "LOW";
        } else if (totalScore < highThresh) {
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
    
    // Mock数据方法，实际应用中应替换为真实的数据获取逻辑
    private int fetchS1(Long userId) { return 85; } // Mock S1
    private int fetchS2(Long userId) { return 70; } // Mock S2
    private int fetchS3(Long userId) { return 90; } // Mock S3
    private int fetchS4(Long userId) { return 80; } // Mock S4
}
