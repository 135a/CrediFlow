package com.crediflow.contract.controller;

import com.crediflow.common.web.Result;
import com.crediflow.contract.service.LoanContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/app/loan-contract")
public class LoanContractController {

    @Autowired
    private LoanContractService loanContractService;

    @PostMapping("/sign")
    public Result<Map<String, Object>> signContract(@RequestHeader("X-User-Id") Long userId,
                                                    @RequestParam Long applicationId,
                                                    @RequestParam BigDecimal amount,
                                                    @RequestParam Integer term,
                                                    @RequestParam boolean agreed) {
        return Result.success(loanContractService.signAndGenerateContract(userId, applicationId, amount, term, agreed));
    }

    @GetMapping("/link")
    public Result<Map<String, Object>> getContractLink(@RequestHeader("X-User-Id") Long userId,
                                                       @RequestParam Long applicationId) {
        return Result.success(loanContractService.getContractLink(userId, applicationId));
    }
}
