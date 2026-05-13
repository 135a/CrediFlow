package com.crediflow.repayment.controller;

import com.crediflow.common.web.Result;
import com.crediflow.repayment.service.RepaymentPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/repayment")
public class RepaymentController {

    @Autowired
    private RepaymentPlanService repaymentPlanService;

    @PostMapping("/pay/{planId}")
    @com.crediflow.common.annotation.Idempotent(key = "'REPAY_APP:' + #idmpToken")
    public Result<Void> pay(@PathVariable Long planId, 
                            @RequestHeader("X-User-Id") Long userId,
                            @RequestHeader("Idempotency-Key") String idmpToken) {
        repaymentPlanService.processRepayment(planId, userId);
        return Result.success(null);
    }
}
