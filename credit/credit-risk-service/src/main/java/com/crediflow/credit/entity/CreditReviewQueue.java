package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("cf_credit_review_queue")
public class CreditReviewQueue {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long applicationId;
    private Long userId;
    private String sceneType; // CREDIT, LOAN
    
    // AI 辅助三件套
    private String riskDetails; // JSON string of list
    private Double defaultProbability;
    private Double fraudProbability;
    private String aiSuggestion;
    
    private String status; // PENDING, APPROVED, REJECTED
    private String reviewRemark;
    private Date createdAt;
    private Date updatedAt;
}
