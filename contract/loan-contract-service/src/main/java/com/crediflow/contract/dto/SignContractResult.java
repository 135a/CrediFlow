package com.crediflow.contract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同签署接口的业务返回体，替代 {@code Map<String, Object>}，便于编译期约束字段与序列化契约（与历史 JSON 字段 {@code status}/{@code message} 对齐）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignContractResult {

    /**
     * 业务状态，如 SUCCESS（与前端/调用方既有约定保持一致）
     */
    private String status;

    /**
     * 人类可读说明；幂等命中「已签署」等场景可携带提示文案
     */
    private String message;
}
