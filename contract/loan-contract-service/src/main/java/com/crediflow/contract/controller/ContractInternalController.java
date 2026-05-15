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
     * @return 返回包含合同状态和合同号的Map对象，如果未找到合同则状态为"NOT_FOUND"
     */
    @GetMapping("/credit-status")
    public Result<Map<String, Object>> getCreditContractStatus(@RequestParam("userId") Long userId) {
        // 构建查询条件：查询指定用户的最新信用合同
        LambdaQueryWrapper<LoanContract> query = new LambdaQueryWrapper<>();
        query.eq(LoanContract::getUserId, userId)  // 设置用户ID条件
             .eq(LoanContract::getContractType, "CREDIT_CONTRACT")  // 设置合同类型为信用合同
             .orderByDesc(LoanContract::getCreatedAt)  // 按创建时间降序排序
             .last("LIMIT 1");  // 只取最新的一条记录
             
        // 执行查询，获取最新信用合同
        LoanContract contract = loanContractService.getOne(query);
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        if (contract != null) {
            // 如果找到合同，返回合同状态和合同号
            result.put("status", contract.getStatus());
            result.put("contractNo", contract.getContractNo());
        } else {
            // 如果未找到合同，返回"NOT_FOUND"状态
            result.put("status", "NOT_FOUND");
        }
        
        // 返回成功结果
        return Result.success(result);
    }
}
