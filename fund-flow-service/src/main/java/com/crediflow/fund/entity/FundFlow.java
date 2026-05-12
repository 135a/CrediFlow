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
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowNo;
    private Long applicationId;
    private Long userId;
    private BigDecimal amount;
    private String type;
    private String status;
    private String thirdPartyTradeNo;
    private Date createdAt;
    private Date updatedAt;
}
