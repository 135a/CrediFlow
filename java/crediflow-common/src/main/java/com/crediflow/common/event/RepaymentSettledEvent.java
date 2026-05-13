package com.crediflow.common.event;

import lombok.Data;

/**
 * 由 Go {@code fund-channel-gateway} 在收到资金方异步回调后投递的还款/代扣终态事件。
 *
 * <p>对应 Go 侧 {@code go/fund-channel-gateway/mq/events.go RepaymentEvent}；
 * 字段顺序与 JSON key 必须保持一致。{@code triggerSource} 区分主动还款（active）
 * 与定时代扣（scheduler），便于消费者按链路分流。</p>
 */
@Data
public class RepaymentSettledEvent {
    private String eventType;
    private String gatewayRequestId;
    private String providerId;
    private String businessOrderNo;
    private String loanNo;
    private String userId;
    private Integer installment;
    private String amount;
    private String currency;
    /** SUCCESS / FAILED */
    private String terminal;
    private String providerTxnNo;
    private String failureCode;
    private String failureReason;
    /** active / scheduler */
    private String triggerSource;
    private String payloadDigest;
    private String occurredAt;
}
