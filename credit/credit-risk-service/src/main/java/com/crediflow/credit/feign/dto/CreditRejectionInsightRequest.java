package com.crediflow.credit.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 信用拒绝洞察请求类
 * 用于封装信用拒绝分析的请求参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditRejectionInsightRequest {
    // 规则摘要列表，包含多个规则的简要描述信息
    private List<String> ruleSummaries;
}
