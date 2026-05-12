package com.crediflow.repayment.controller;

import com.crediflow.common.web.Result;
import com.crediflow.repayment.entity.RepaymentPlan;
import com.crediflow.repayment.service.RepaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/app/repayment")
public class RepaymentController {

    @Autowired
    private RepaymentService repaymentService;

    @PostMapping("/generate")
    public Result<List<RepaymentPlan>> generatePlans(@RequestHeader("X-User-Id") Long userId,
                                                     @RequestParam Long contractId,
                                                     @RequestParam BigDecimal loanAmount,
                                                     @RequestParam BigDecimal interestRate,
                                                     @RequestParam Integer term) {
        return Result.success(repaymentService.generatePlans(userId, contractId, loanAmount, interestRate, term));
    }

    @PostMapping("/active-repay")
    public Result<RepaymentPlan> activeRepay(@RequestHeader("X-User-Id") Long userId,
                                             @RequestParam Long planId,
                                             @RequestParam String idmpToken) {
        return Result.success(repaymentService.activeRepay(userId, planId, idmpToken));
    }
}
