package com.crediflow.common.messaging;

/**
 * 主题命名约定：领域前缀 + 事件名（小写，点分可选）。
 */
public final class EventTopicNames {

    public static final String DOMAIN_LOAN = "crediflow.loan";
    public static final String TOPIC_LOAN_DISBURSED = DOMAIN_LOAN + ".disbursed";
    public static final String TOPIC_LOAN_REPAYMENT = DOMAIN_LOAN + ".repayment.completed";

    private EventTopicNames() {
    }
}
