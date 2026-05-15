package com.crediflow.credit.feign.dto;

import lombok.Data;

import java.util.List;

import lombok.Data; // 使用Lombok的@Data注解自动生成getter、setter、toString等方法
/**
 * 手工复核助手响应类
 * 用于封装系统返回给用户的手工复核相关信息
 */
@Data
public class ManualReviewAssistantResponse {
    /** 风险详情列表，包含具体的风险描述信息 */
    private List<String> riskDetails;
    /** 默认风险概率值，范围0.0-1.0之间 */
    private Double defaultProbability;
    /** 欺诈风险概率值，范围0.0-1.0之间 */
    private Double fraudProbability;
    /** 系统给出的处理建议信息 */
    private String suggestion;
}
