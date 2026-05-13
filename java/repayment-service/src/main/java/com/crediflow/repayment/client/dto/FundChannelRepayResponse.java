package com.crediflow.repayment.client.dto;

import lombok.Data;

/**
 * Go 网关同步受理响应；终态仅能通过 RocketMQ {@code REPAYMENT_SETTLED_EVENT}
 * 桥接事件到达。
 */
@Data
public class FundChannelRepayResponse {
    private String state;
    private String gatewayRequestId;
    private String providerId;
    private String businessOrderNo;
    private String errorCode;
    private String errorMessage;
}
