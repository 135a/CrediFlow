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

    /** user-service：KYC v2（实名+实人）通过后投递。 */
    String TOPIC_KYC_PASSED = "KYC_PASSED_TOPIC";
    String TAG_KYC_PASSED = "KYC_PASSED_EVENT";

    // ── 授信生命周期 Credit Lifecycle ──
    String TOPIC_CREDIT_LIFECYCLE = "credit-lifecycle-topic";
    String TAG_CREDIT_APPROVED = "CREDIT_APPROVED_EVENT";
    String TAG_CREDIT_REJECTED = "CREDIT_REJECTED_EVENT";
    String TAG_CREDIT_MANUAL_REVIEW = "CREDIT_MANUAL_REVIEW_EVENT";
    String TAG_CREDIT_REVIEW_DECIDED = "CREDIT_REVIEW_DECIDED_EVENT";

    // ── 对话意图风控升级 Chat Intent Risk Escalation ──
    String TOPIC_CREDIT_CHAT_RISK = "credit-chat-risk-topic";
    String TAG_CHAT_RISK_ESCALATION = "CHAT_RISK_ESCALATION_EVENT";
}
