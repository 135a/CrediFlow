package com.crediflow.credit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 用户最新授信申请状态视图。
 * status 为 {@link com.crediflow.credit.enums.CreditApplicationStatus#getCode()} 或哨兵值 "NOT_APPLIED"。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditApplicationStatusView {
    private String status;
    private Long applicationId;
    private Boolean secondaryFaceRequired;
}
