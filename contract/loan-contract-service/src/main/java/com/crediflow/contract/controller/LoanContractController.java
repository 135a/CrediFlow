package com.crediflow.contract.controller;

import com.crediflow.common.web.Result;
import com.crediflow.contract.service.LoanContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 贷款合同控制器
 * 提供贷款合同相关的API接口，包括合同签署和获取合同链接功能
 */
@RestController
@RequestMapping("/api/internal/contract")
public class LoanContractController {

    /**
     * 注入贷款合同服务
     * 用于处理贷款合同相关的业务逻辑
     */
    @Autowired
    private LoanContractService loanContractService;

    /**
     * 签署贷款合同接口
     *
     * @param userId 用户ID，从请求头中获取
     * @param applicationId 贷款申请ID
     * @param amount 贷款金额
     * @param term 贷款期限
     * @param agreed 是否同意合同条款
     * @return 返回处理结果，包含合同相关信息
     */
    @PostMapping("/sign")
    public Result<Map<String, Object>> signContract(@RequestHeader("X-User-Id") Long userId,
                                                    @RequestParam Long applicationId,
                                                    @RequestParam BigDecimal amount,
                                                    @RequestParam Integer term,
                                                    @RequestParam boolean agreed) {
        return Result.success(loanContractService.signAndGenerateContract(userId, applicationId, amount, term, agreed));
    }

    /**
     * 获取合同链接接口
     *
     * @param userId 用户ID，从请求头中获取
     * @param applicationId 贷款申请ID
     * @return 返回处理结果，包含合同链接信息
     */
    @GetMapping("/link")
    public Result<Map<String, Object>> getContractLink(@RequestHeader("X-User-Id") Long userId,
                                                       @RequestParam Long applicationId) {
        return Result.success(loanContractService.getContractLink(userId, applicationId));
    }
}
