package com.crediflow.contract.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.contract.dto.ContractLinkResult;
import com.crediflow.contract.dto.CreditContractStatusResult;
import com.crediflow.contract.dto.SignContractResult;
import com.crediflow.contract.entity.LoanContract;

/**
 * 贷款合同服务接口，继承自IService<LoanContract>
 * 提供贷款合同相关的业务方法定义
 */
public interface LoanContractService extends IService<LoanContract> {
    /**
     * 根据申请ID和用户ID生成贷款合同（幂等：已存在则不再插入）
     * @param applicationId 申请ID
     * @param userId 用户ID
     * @param contractType 合同类型
     * @return {@code true} 表示本次新插入了 INIT 合同；{@code false} 表示已存在合同，未写库
     */
    boolean generateContract(Long applicationId, Long userId, String contractType);
    /**
     * 签署并生成贷款合同
     * @param userId 用户ID
     * @param applicationId 申请ID
     * @param amount 贷款金额
     * @param term 贷款期限
     * @param agreed 是否同意合同条款
     * @return 签约业务结果（强类型 DTO）
     */
    SignContractResult signAndGenerateContract(Long userId, Long applicationId, java.math.BigDecimal amount, Integer term, boolean agreed);
    /**
     * 获取合同链接
     * @param userId 用户ID
     * @param applicationId 申请ID
     * @return 合同链接（强类型，字段名与历史 JSON 一致）
     */
    ContractLinkResult getContractLink(Long userId, Long applicationId);
    /**
     * 生成收据和还款计划
     * @param applicationId 申请ID
     * @param userId 用户ID
     * @param amount 贷款金额
     * @param term 贷款期限
     */
    void generateReceiptAndPlan(Long applicationId, Long userId, java.math.BigDecimal amount, Integer term);
    
    /**
     * 获取指定用户的最新信用合同状态
     * @param userId 用户ID
     * @return 最新一条信用合同的状态快照（强类型）
     */
    CreditContractStatusResult getLatestCreditContractStatus(Long userId);
}
