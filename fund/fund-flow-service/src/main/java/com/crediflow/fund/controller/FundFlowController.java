package com.crediflow.fund.controller;
import com.crediflow.common.web.Result;
import com.crediflow.fund.service.FundFlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FundFlowController {

    @Autowired
    private FundFlowService fundFlowService;

    // 内部服务调用：记录资金流水
    @PostMapping("/internal/fund-flow/record")
    public Result<Void> recordFlow(@RequestParam Long userId,
                                   @RequestParam String type,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam String tradeNo,
                                   @RequestParam String status) {
        fundFlowService.recordFlow(userId, type, amount, tradeNo, status);
        return Result.success(null);
    }

    // 第三方支付回调接口：无需登录态鉴权
    @PostMapping("/callback/payment")
    public String paymentCallback(@RequestParam Map<String, String> params) {
        boolean valid = fundFlowService.verifyThirdPartyCallback(params);
        if (valid) {
            // 处理流水状态更新
            return "SUCCESS";
        }
        return "FAIL";
    }
}
