package com.crediflow.credit.service.scoring;

import lombok.Data;

/**
 * 分数详情类
 * 用于存储和管理各项分数信息，包括各科目分数、总分、风险等级和规则版本
 */
@Data  // 使用Lombok的@Data注解，自动生成getter、setter、toString等方法
public class ScoreDetail {
    private int s1Score;      // 科目1的分数
    private int s2Score;      // 科目2的分数
    private int s3Score;      // 科目3的分数
    private int s4Score;      // 科目4的分数
    private double totalScore; // 总分，使用double类型以支持更精确的计算
    private String riskLevel; // LOW, MEDIUM, HIGH
    private String rulesVersion;
}
