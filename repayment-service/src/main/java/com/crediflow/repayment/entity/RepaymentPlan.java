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
    private Long contractId;
    private Long applicationId;
    private Long userId;
    private Integer period;
    private Integer totalTerms;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal penalty;
    private BigDecimal totalAmount;
    private Date dueDate;
    private Date paidTime;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
