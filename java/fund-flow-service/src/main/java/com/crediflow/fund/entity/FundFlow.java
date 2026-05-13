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
    /** 资金方标识（混合路由时由业务或网关解析） */
    private String providerId;
    /** Go 资金网关受理号 */
    private String gatewayRequestId;
    /** 资金方侧流水号（终态回调后回填） */
    private String providerTxnNo;
    /** 桥接事件或回调报文摘要 */
    private String payloadDigest;
    private Date createdAt;
    private Date updatedAt;
}
