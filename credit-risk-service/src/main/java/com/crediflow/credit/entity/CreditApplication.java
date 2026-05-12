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
    private String status; // PENDING, APPROVED, REJECTED
    private String auditReason;
    private Date createdAt;
    private Date updatedAt;
}
