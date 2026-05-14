package com.crediflow.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_loan_receipt")
public class LoanReceipt {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String receiptNo;
    private Long applicationId;
    private Long userId;
    private BigDecimal principalAmount;
    private BigDecimal annualInterestRate;
    private Integer totalTerms;
    private String status; // ACTIVE, SETTLED, OVERDUE
    private Date createdAt;
    private Date updatedAt;
}
