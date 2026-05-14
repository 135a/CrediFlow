package com.crediflow.common.event;

/**
 * 消息队列常量接口，定义了系统中使用的所有消息主题(Topics)和标签(Tags)
 * 该接口主要用于消息队列的常量定义，确保消息主题和标签的一致性
 */
public interface MqConstants {
    // Topics - 消息主题定义
    String TOPIC_LOAN_LIFECYCLE = "loan-lifecycle-topic";  // 贷款生命周期主题

    // Tags (Events) - 事件标签定义
    String TAG_LOAN_APPROVED = "LOAN_APPROVED_EVENT";         // 贷款批准事件标签
    String TAG_CONTRACT_READY = "CONTRACT_READY_EVENT";      // 合同准备就绪事件标签
    String TAG_FUND_DISBURSED = "FUND_DISBURSED_EVENT";      // 资金拨付事件标签

    // ── Go fund-channel-gateway 桥接终态事件（独立 Topic，区别于 LoanLifecycleMessage 流） ──
    // 由 fund-channel-gateway 接收资金方异步回调后投递；负载结构与
    // {@link FundDisbursedTerminalEvent} / {@link RepaymentSettledEvent} 一致。
    String TOPIC_FUND_DISBURSED_TERMINAL = "FUND_DISBURSED_EVENT";  // 资金拨付终态事件主题
    String TOPIC_REPAYMENT_SETTLED = "REPAYMENT_SETTLED_EVENT";     // 还款结算事件主题

    /** user-service：KYC v2（实名+实人）通过后投递。 */
    String TOPIC_KYC_PASSED = "KYC_PASSED_TOPIC";          // KYC认证通过主题
    String TAG_KYC_PASSED = "KYC_PASSED_EVENT";            // KYC认证通过事件标签

    // ── 授信生命周期 Credit Lifecycle ──
    String TOPIC_CREDIT_LIFECYCLE = "credit-lifecycle-topic";  // 授信生命周期主题
    String TAG_CREDIT_APPROVED = "CREDIT_APPROVED_EVENT";       // 授信批准事件标签
    String TAG_CREDIT_REJECTED = "CREDIT_REJECTED_EVENT";       // 授信拒绝事件标签
    String TAG_CREDIT_MANUAL_REVIEW = "CREDIT_MANUAL_REVIEW_EVENT";  // 授信人工审核事件标签
    String TAG_CREDIT_REVIEW_DECIDED = "CREDIT_REVIEW_DECIDED_EVENT"; // 授信审核决定事件标签

    // ── 对话意图风控升级 Chat Intent Risk Escalation ──
    String TOPIC_CREDIT_CHAT_RISK = "credit-chat-risk-topic";  // 授信对话风控主题
    String TAG_CHAT_RISK_ESCALATION = "CHAT_RISK_ESCALATION_EVENT";  // 对话风险升级事件标签
}
