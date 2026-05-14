package com.crediflow.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_repayment_plan")
public class RepaymentPlan {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long receiptId;
    private Long userId;
    private Integer termNo;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private Date dueDate;
    private String status; // PENDING, PAID, OVERDUE
    private Date createdAt;
    private Date updatedAt;
}
