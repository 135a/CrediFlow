package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_credit_result")
public class CreditResult {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private BigDecimal creditAmount;
    private BigDecimal usedAmount;
    private String status; // ACTIVE, FROZEN, EXPIRED
    private Date expireTime;
    private Date createdAt;
    private Date updatedAt;
}
