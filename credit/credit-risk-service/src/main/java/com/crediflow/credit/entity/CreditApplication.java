package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crediflow.credit.enums.CreditApplicationStatus;
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

    /** 申请流程状态，与库表 status 列枚举值一致 */
    private CreditApplicationStatus status;

    /** 模型风险档：LOW / MEDIUM / HIGH */
    private String modelRiskLevel;
    private Boolean secondaryFaceRequired;
    private String auditReason;
    private String riskInsight;
    private String userSafeInsight;
    private Date createdAt;
    private Date updatedAt;
}
