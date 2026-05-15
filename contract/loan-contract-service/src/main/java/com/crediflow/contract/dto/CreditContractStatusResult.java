package com.crediflow.contract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户最新一条信用合同查询结果，替代 {@code Map<String, Object>}。
 * <p>
 * {@link #status}：有记录时为合同业务状态（如 INIT/SIGNED）；无记录时为字面量 {@code NOT_FOUND}，与历史接口约定一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditContractStatusResult {

    private String status;

    private String contractNo;
}
