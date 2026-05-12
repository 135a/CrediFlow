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
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long userId;
    private Integer termIndex;
    private Integer totalTerms;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private BigDecimal penaltyAmount;
    private BigDecimal totalAmount;
    private Date dueDate;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
