package com.crediflow.fund.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.fund.entity.FundFlow;

import java.math.BigDecimal;
import java.util.Map;

public interface FundFlowService extends IService<FundFlow> {
    void recordFlow(Long userId, String type, BigDecimal amount, String tradeNo, String status);
    boolean verifyThirdPartyCallback(Map<String, String> params);
}
