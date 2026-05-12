package com.crediflow.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_loan_application")
public class LoanApplication {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String applicationNo;
    private Long userId;
    private BigDecimal applyAmount;
    private Integer term;
    private String status; // PENDING, APPROVED, REJECTED
    private Date createdAt;
    private Date updatedAt;
}
