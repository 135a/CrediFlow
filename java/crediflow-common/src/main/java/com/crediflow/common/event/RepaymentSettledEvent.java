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
    // 事件类型，标识事件的种类
    private String eventType;
    // 网关请求ID，用于唯一标识一次请求
    private String gatewayRequestId;
    // 提供商ID，标识资金方或支付渠道提供商
    private String providerId;
    // 业务订单号，业务系统中的唯一订单标识
    private String businessOrderNo;
    // 贷款编号，关联具体的贷款产品
    private String loanNo;
    // 用户ID，标识借款人
    private String userId;
    // 期数，用于分期还款场景
    private Integer installment;
    // 金额，交易的具体金额
    private String amount;
    // 货币类型，如CNY、USD等
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
