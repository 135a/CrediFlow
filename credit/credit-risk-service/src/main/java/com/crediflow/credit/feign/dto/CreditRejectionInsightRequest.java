package com.crediflow.credit.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditRejectionInsightRequest {
    private List<String> ruleSummaries;
}
