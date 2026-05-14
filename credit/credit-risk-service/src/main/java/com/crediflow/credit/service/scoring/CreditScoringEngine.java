package com.crediflow.credit.service.scoring;

public interface CreditScoringEngine {
    /**
     * 计算用户的信用评分及风险等级
     * @param userId 用户ID
     * @return 评分详情
     */
    ScoreDetail calculateScore(Long userId);
    ScoreDetail calculateLoanScore(Long userId);
}
