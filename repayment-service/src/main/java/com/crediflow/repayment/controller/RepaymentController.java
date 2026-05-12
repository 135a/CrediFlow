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
    public Result<Void> pay(@PathVariable Long planId, @RequestHeader("X-User-Id") Long userId) {
        repaymentPlanService.processRepayment(planId, userId);
        return Result.success(null);
    }
}
