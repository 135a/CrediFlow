package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_credit_application")
public class CreditApplication {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private BigDecimal applyAmount;
    private BigDecimal suggestedAmount;
    public static final String STATUS_PENDING_HARD_RULES = "PENDING_HARD_RULES";
    public static final String STATUS_PENDING_SCORING = "PENDING_SCORING";
    public static final String STATUS_PENDING_ROUTING = "PENDING_ROUTING";
    public static final String STATUS_PENDING_SECONDARY_FACE = "PENDING_SECONDARY_FACE";
    public static final String STATUS_PENDING_MANUAL_REVIEW = "PENDING_MANUAL_REVIEW";
    public static final String STATUS_CONTRACT_PENDING = "CONTRACT_PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private String status; // Using the constants above
    private String modelRiskLevel; // LOW, MEDIUM, HIGH
    private Boolean secondaryFaceRequired;
    private String auditReason;
    private String riskInsight;
    private String userSafeInsight;
    private Date createdAt;
    private Date updatedAt;
}
