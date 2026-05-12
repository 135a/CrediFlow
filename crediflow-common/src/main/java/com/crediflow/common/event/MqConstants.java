package com.crediflow.common.event;

public interface MqConstants {
    // Topics
    String TOPIC_LOAN_LIFECYCLE = "loan-lifecycle-topic";

    // Tags (Events)
    String TAG_LOAN_APPROVED = "LOAN_APPROVED_EVENT";
    String TAG_CONTRACT_READY = "CONTRACT_READY_EVENT";
    String TAG_FUND_DISBURSED = "FUND_DISBURSED_EVENT";
}
