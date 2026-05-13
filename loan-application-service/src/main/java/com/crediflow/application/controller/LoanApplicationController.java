package com.crediflow.application.controller;

import com.crediflow.application.entity.LoanApplication;
import com.crediflow.application.service.LoanApplicationService;
import com.crediflow.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/app/loan-application")
public class LoanApplicationController {

    @Autowired
    private LoanApplicationService loanApplicationService;

    @PostMapping("/apply")
    public Result<LoanApplication> applyLoan(@RequestHeader("X-User-Id") Long userId,
                                             @RequestParam("applyAmount") BigDecimal applyAmount,
                                             @RequestParam("term") Integer term,
                                             @RequestParam("idmpToken") String idmpToken) {
        return Result.success(loanApplicationService.applyLoan(userId, applyAmount, term, idmpToken));
    }
}
