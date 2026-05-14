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
    /** PENDING / OVERDUE / SUBMITTED / PAID / FAILED */
    private String status;
    /** 资金方标识；空表示走 Nacos defaultProviderId（混合路由）。 */
    private String providerId;
    /** Go 资金网关受理号；由网关同步响应回填。 */
    private String gatewayRequestId;
    /** 资金方侧流水号；由网关桥接的终态事件回填。 */
    private String providerTxnNo;
    /** 主动还款受理时间；用于中间态轮询与超时分析。 */
    private Date submittedAt;
    private Date createdAt;
    private Date updatedAt;
}
