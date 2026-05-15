package com.crediflow.credit.feign.dto;

import lombok.Data;

/**
 * 信贷拒绝洞察响应类
 * 用于封装信贷拒绝后的相关信息和建议
 */
@Data  // Lombok注解，自动生成getter、setter、toString等方法
public class CreditRejectionInsightResponse {
    // 用户安全洞察信息，向用户解释拒绝原因的安全相关部分
    private String userSafeInsight;
    // 管理员洞察信息，内部使用的拒绝原因详情
    private String adminInsight;
    // 可操作建议，提供用户可以采取的后续行动建议
    private String actionableAdvice;
}
