package com.crediflow.contract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同下载/预览链接查询结果，替代 {@code Map<String, Object>}，与 JSON 字段 {@code link} 对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractLinkResult {

    private String link;
}
