package com.crediflow.repayment.entity;

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
    private Long contractId;
    private Long userId;
    private Integer period;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal penalty;
    private String status; // PENDING, PAID, OVERDUE
    private Date dueDate;
    private Date paidTime;
    private Date createdAt;
    private Date updatedAt;
}
