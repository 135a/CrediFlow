package com.crediflow.common.event;

import lombok.Data;

@Data
public class LoanLifecycleMessage {
    private Long loanApplicationId;
    private Long userId;
    private String eventType;
    private Object payload; // Can store extra JSON data if needed
}
