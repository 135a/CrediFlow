package com.crediflow.credit.dto;

import lombok.Data;

import java.util.List;

@Data
public class RiskSignalEscalateRequest {
    private Long userId;
    private List<String> relevantChatLogs;
    private String agentSuggestions;
    private String riskType;
}
