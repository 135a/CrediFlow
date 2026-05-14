package com.crediflow.contract.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.web.Result;
import com.crediflow.contract.entity.LoanContract;
import com.crediflow.contract.service.LoanContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/contract")
public class ContractInternalController {

    @Autowired
    private LoanContractService loanContractService;

    @GetMapping("/credit-status")
    public Result<Map<String, Object>> getCreditContractStatus(@RequestParam("userId") Long userId) {
        LambdaQueryWrapper<LoanContract> query = new LambdaQueryWrapper<>();
        query.eq(LoanContract::getUserId, userId)
             .eq(LoanContract::getContractType, "CREDIT_CONTRACT")
             .orderByDesc(LoanContract::getCreatedAt)
             .last("LIMIT 1");
             
        LoanContract contract = loanContractService.getOne(query);
        
        Map<String, Object> result = new HashMap<>();
        if (contract != null) {
            result.put("status", contract.getStatus());
            result.put("contractNo", contract.getContractNo());
        } else {
            result.put("status", "NOT_FOUND");
        }
        
        return Result.success(result);
    }
}
