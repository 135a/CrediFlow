package com.crediflow.repayment.client.dto;

import lombok.Data;

import java.util.Map;

/**
 * 调用 Go {@code fund-channel-gateway} 主动还款 / 代扣受理接口的请求体。
 *
 * <p>结构与 fund-flow-service 的 {@code FundChannelDisburseRequest} 对齐：
 * {@code bindCardId} 必须为绑卡 token；明文卡号永远不得跨内网传递。</p>
 *
 * <p>{@code triggerSource} 取 {@code active}（主动还款）或 {@code scheduler}（定时代扣，
 * 通常由 batch-service 直接调用网关，这里仅保留主动还款使用 {@code active}）。</p>
 */
@Data
public class FundChannelRepayRequest {
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
