package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_user_credit_quota")
public class UserCreditQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal usedAmount;
    private BigDecimal availableAmount;
    private BigDecimal frozenAmount;
    @Version
    private Integer version;
    private Date createdAt;
    private Date updatedAt;
}
