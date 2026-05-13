package com.crediflow.common.event;

public interface MqConstants {
    // Topics
    String TOPIC_LOAN_LIFECYCLE = "loan-lifecycle-topic";

    // Tags (Events)
    String TAG_LOAN_APPROVED = "LOAN_APPROVED_EVENT";
    String TAG_CONTRACT_READY = "CONTRACT_READY_EVENT";
    String TAG_FUND_DISBURSED = "FUND_DISBURSED_EVENT";

    // ── Go fund-channel-gateway 桥接终态事件（独立 Topic，区别于 LoanLifecycleMessage 流） ──
    // 由 fund-channel-gateway 接收资金方异步回调后投递；负载结构与
    // {@link FundDisbursedTerminalEvent} / {@link RepaymentSettledEvent} 一致。
    String TOPIC_FUND_DISBURSED_TERMINAL = "FUND_DISBURSED_EVENT";
    String TOPIC_REPAYMENT_SETTLED = "REPAYMENT_SETTLED_EVENT";
}
