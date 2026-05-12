package com.crediflow.fund.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.fund.entity.FundFlow;
import com.crediflow.fund.mapper.FundFlowMapper;
import com.crediflow.fund.service.FundFlowService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Service
public class FundFlowServiceImpl extends ServiceImpl<FundFlowMapper, FundFlow> implements FundFlowService {

    @Override
    public void recordFlow(Long userId, String type, BigDecimal amount, String tradeNo, String status) {
        FundFlow flow = new FundFlow();
        flow.setUserId(userId);
        flow.setType(type);
        flow.setAmount(amount);
        flow.setTradeNo(tradeNo);
        flow.setStatus(status);
        flow.setTradeTime(new Date());
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        this.save(flow);
        
        System.out.println("FUND_FLOW: Recorded " + type + " of amount " + amount + " for user " + userId);
    }

    @Override
    public boolean verifyThirdPartyCallback(Map<String, String> params) {
        // 第三方支付回调验签占位
        // 通常需要使用公钥验证 params 中的 sign 字段
        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        System.out.println("VERIFY_SIGN: Verifying signature: " + sign);
        return true;
    }
}
