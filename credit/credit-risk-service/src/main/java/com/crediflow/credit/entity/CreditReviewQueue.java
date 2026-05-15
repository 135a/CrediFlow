package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crediflow.credit.enums.ReviewQueueStatus;
import com.crediflow.credit.enums.ReviewSceneType;
import lombok.Data;

import java.util.Date;

@Data
@TableName("cf_credit_review_queue")
public class CreditReviewQueue {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long applicationId;
    private Long userId;

    private ReviewSceneType sceneType;

    private String riskDetails;
    private Double defaultProbability;
    private Double fraudProbability;
    private String aiSuggestion;

    private ReviewQueueStatus status;
    private String reviewRemark;
    private Date createdAt;
    private Date updatedAt;
}
