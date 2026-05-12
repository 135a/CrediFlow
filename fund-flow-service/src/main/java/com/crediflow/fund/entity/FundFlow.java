package com.crediflow.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_fund_flow")
public class FundFlow {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String tradeNo; // 第三方流水号
    private String type; // DISBURSE (放款), REPAY (还款)
    private BigDecimal amount;
    private String status; // SUCCESS, FAILED
    private Date tradeTime;
    private Date createdAt;
    private Date updatedAt;
}
