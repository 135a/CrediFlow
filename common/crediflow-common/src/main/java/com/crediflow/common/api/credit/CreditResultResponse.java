package com.crediflow.common.api.credit;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 授信结果视图，供跨服务 Feign 调用（credit-risk-service → loan-application-service）。
 */
@Data
public class CreditResultResponse {
    private Long id;
    private Long userId;
    private BigDecimal creditAmount;
    private BigDecimal usedAmount;
    private String status;
    private java.util.Date expireTime;
}
