package com.crediflow.credit.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手工审核评分详情类
 * 用于存储手工审核的总分和风险等级信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewScoreDetail {
    // 总分，用于存储审核的总体得分
    private double totalScore;
    // 风险等级，用于标识审核对象的风险级别
    private String riskLevel;
}
