package com.crediflow.fund.client.dto;

import lombok.Data;

import java.util.Map;

/**
 * 与 Go {@code fund-channel-gateway} 的 {@code /internal/v1/disburse} 请求体对齐。
 * {@code bindCardId} 必须为绑卡 token / 引用 ID，禁止传明文卡号。
 */
@Data
public class FundChannelDisburseRequest {
    private String providerId;
    private String businessOrderNo;
    private String userId;
    private String bindCardId;
    private String amount;
    private String currency;
    private Integer installments;
    private String triggerSource;
    private Map<String, String> extra;
}
