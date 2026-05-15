package com.crediflow.contract.controller;

import com.crediflow.common.web.Result;
import com.crediflow.contract.dto.CreditContractStatusResult;
import com.crediflow.contract.service.LoanContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同内部控制器
 * 提供合同相关的内部API接口
 */
@RestController
@RequestMapping("/api/internal/contract")
public class ContractInternalController {

    /**
     * 自动注入贷款合同服务
     * 用于处理合同相关的业务逻辑
     */
    @Autowired
    private LoanContractService loanContractService;

    /**
     * 获取信用合同状态接口
     *
     * @param userId 用户ID，用于查询指定用户的合同信息
     * @return 合同状态与合同号；无记录时 status 为 NOT_FOUND
     */
    @GetMapping("/credit-status")
    public Result<CreditContractStatusResult> getCreditContractStatus(@RequestParam("userId") Long userId) {
        return Result.success(loanContractService.getLatestCreditContractStatus(userId));
    }
}
