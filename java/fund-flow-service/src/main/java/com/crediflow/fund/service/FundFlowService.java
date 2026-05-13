package com.crediflow.fund.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.fund.entity.FundFlow;

public interface FundFlowService extends IService<FundFlow> {
    void processDisbursement(Long applicationId, Long userId);
    void recordFlow(Long userId, String type, java.math.BigDecimal amount, String tradeNo, String status);
    boolean verifyThirdPartyCallback(java.util.Map<String, String> params);
}
