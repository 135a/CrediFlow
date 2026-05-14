package com.crediflow.common.event;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 由 Go {@code fund-channel-gateway} 在收到资金方异步回调后投递的放款终态事件。
 *
 * <p>对应 Go 侧 {@code go/fund-channel-gateway/mq/events.go DisbursementEvent}；
 * 命名风格、字段顺序与 JSON 序列化必须严格保持一致，禁止改名。</p>
 *
 * <p>Go 侧 amount 为十进制字符串（避免 float 精度丢失），Java 侧使用 BigDecimal 接收，
 * Jackson 可自动将 JSON 字符串 "12000.00" 反序列化为 BigDecimal，序列化兼容。</p>
 *
 * <p>消费方（fund-flow-service、post-loan-service 等）MUST 以 {@code gatewayRequestId}
 * 或 {@code providerTxnNo} 作为幂等键，重复投递 MUST NOT 产生矛盾的终态。</p>
 */
@Data
public class FundDisbursedTerminalEvent {
    // 事件类型标识
    private String eventType;
    // 网关请求ID，用于幂等控制
    private String gatewayRequestId;
    // 资金提供方ID
    private String providerId;
    // 业务订单号
    private String businessOrderNo;
    // 申请ID
    private String applicationId;
    // 用户ID
    private String userId;
    // 金额（Go 侧为十进制字符串，Java 侧用 BigDecimal 精确表示）
    private BigDecimal amount;
    // 货币类型
    private String currency;
    /** SUCCESS / FAILED */
    /**
     * 终端标识
     */
    private String terminal;
    /**
     * 提供者交易号
     */
    private String providerTxnNo;
    /**
     * 失败代码
     */
    private String failureCode;
    /**
     * 失败原因
     */
    private String failureReason;
    /**
     * 有效载荷摘要
     */
    private String payloadDigest;
    /** ISO-8601 UTC，由网关侧统一序列化为 RFC3339。 */
    private String occurredAt;
}
