package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("cf_credit_score")
public class CreditScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String applicationId;
    private Integer s1Score;
    private Integer s2Score;
    private Integer s3Score;
    private Integer s4Score;
    private Double totalScore;
    private String riskLevel;
    private String rulesVersion;
    private Date createdAt;
    private Date updatedAt;
}
