package com.crediflow.common.event;

import lombok.Data;

/**
 * 由 Go {@code fund-channel-gateway} 在收到资金方异步回调后投递的放款终态事件。
 *
 * <p>对应 Go 侧 {@code go/fund-channel-gateway/mq/events.go DisbursementEvent}；
 * 命名风格、字段顺序与 JSON 序列化必须严格保持一致，禁止改名。</p>
 *
 * <p>消费方（fund-flow-service、post-loan-service 等）MUST 以 {@code gatewayRequestId}
 * 或 {@code providerTxnNo} 作为幂等键，重复投递 MUST NOT 产生矛盾的终态。</p>
 */
@Data
public class FundDisbursedTerminalEvent {
    private String eventType;
    private String gatewayRequestId;
    private String providerId;
    private String businessOrderNo;
    private String applicationId;
    private String userId;
    private String amount;
    private String currency;
    /** SUCCESS / FAILED */
    private String terminal;
    private String providerTxnNo;
    private String failureCode;
    private String failureReason;
    private String payloadDigest;
    /** ISO-8601 UTC，由网关侧统一序列化为 RFC3339。 */
    private String occurredAt;
}
