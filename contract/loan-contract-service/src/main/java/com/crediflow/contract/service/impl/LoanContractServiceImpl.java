package com.crediflow.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.contract.dto.ContractLinkResult;
import com.crediflow.contract.dto.CreditContractStatusResult;
import com.crediflow.contract.dto.SignContractResult;
import com.crediflow.contract.entity.LoanContract;
import com.crediflow.contract.entity.LoanReceipt;
import com.crediflow.contract.entity.RepaymentPlan;
import com.crediflow.contract.feign.CreditClient;
import com.crediflow.contract.mapper.LoanContractMapper;
import com.crediflow.contract.mapper.LoanReceiptMapper;
import com.crediflow.contract.mapper.RepaymentPlanMapper;
import com.crediflow.contract.service.LoanContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 贷款合同服务实现类
 * 继承ServiceImpl并提供贷款合同相关的业务逻辑实现
 */
@Service
public class LoanContractServiceImpl extends ServiceImpl<LoanContractMapper, LoanContract> implements LoanContractService {

    /**
     * 借据数据访问层
     */
    @Autowired
    private LoanReceiptMapper loanReceiptMapper;

    /**
     * 还款计划数据访问层
     */
    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    /**
     * 信用额度服务客户端
     */
    @Autowired
    private CreditClient creditClient;

    /**
     * 年利率配置值，默认为0.18
     */
    @Value("${crediflow.loan.rate.annual:0.18}")
    private String annualRateStr;

    /**
     * 生成贷款合同
     * @param applicationId 申请ID
     * @param userId 用户ID
     * @param contractType 合同类型
     * @return 是否本次新插入合同（供 MQ 等调用方决定是否向下游发「合同就绪」类事件）
     */
    @Override
    public boolean generateContract(Long applicationId, Long userId, String contractType) {
        // 构建查询条件，检查是否已存在合同
        LambdaQueryWrapper<LoanContract> query = new LambdaQueryWrapper<>();
        query.eq(LoanContract::getApplicationId, applicationId)
             .eq(LoanContract::getUserId, userId)
             .last("LIMIT 1");
             
        // 查询已存在的合同
        LoanContract existing = this.getOne(query);
        if (existing != null) {
            return false; // 幂等防重：已存在合同则直接返回，未写库
        }

        // 创建新合同对象
        LoanContract contract = new LoanContract();
        contract.setContractNo("CTR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        contract.setApplicationId(applicationId);
        contract.setUserId(userId);
        contract.setContractType(contractType);
        contract.setStatus("INIT"); // 初始生成，等待签署
        
        // TODO: 接入第三方电子签章平台（e签宝/法大大），由平台渲染 PDF 并完成 CA 签名；
        //       签署完成后平台异步回调，后端更新状态 + 归档至 OSS。
        //       当前阶段 contractUrl 暂置空，待签署完成后再由回调写入真实 OSS 链接。
        contract.setContractUrl(null);
        
        // 设置创建和更新时间
        contract.setCreatedAt(new Date());
        contract.setUpdatedAt(new Date());
        this.save(contract);
        return true;
    }

    /**
     * 签署并生成合同
     * @param userId 用户ID
     * @param applicationId 申请ID
     * @param amount 贷款金额
     * @param term 贷款期限
     * @param agreed 是否同意协议
     * @return 签约结果 DTO（与 HTTP 层序列化字段一致）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SignContractResult signAndGenerateContract(Long userId, Long applicationId, BigDecimal amount, Integer term, boolean agreed) {
        // 检查用户是否同意协议
        if (!agreed) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "必须同意协议才能签约");
        }
        
        // Find existing contract
        LambdaQueryWrapper<LoanContract> query = new LambdaQueryWrapper<>();
        query.eq(LoanContract::getApplicationId, applicationId)
             .eq(LoanContract::getUserId, userId)
             .last("LIMIT 1");
             
        LoanContract contract = this.getOne(query);
        
        if (contract != null && "SIGNED".equals(contract.getStatus())) {
            // Already signed, return success directly (idempotent)
            return new SignContractResult("SUCCESS", "Contract already signed");
        }

        if (contract == null) {
            // 返回值在此路径不关心是否新建：HTTP 签约入口不发送 CONTRACT_READY
            this.generateContract(applicationId, userId, "LOAN_CONTRACT");
            // Fetch newly generated contract
            contract = this.getOne(query);
        }
        
        // TODO: 此处为同步模拟，后续将替换为：前端跳电子签 SDK -> 后端接收三方签章异步回调更新 SIGNED 状态
        contract.setStatus("SIGNED");
        contract.setUpdatedAt(new Date());
        this.updateById(contract);
        
        // 合同签署完毕，立刻生成借据和还款计划
        this.generateReceiptAndPlan(applicationId, userId, amount, term);

        // 扣减额度
        Map<String, Object> req = new HashMap<>();
        req.put("userId", userId);
        req.put("amount", amount);
        Result<Void> deductResult = creditClient.deductQuota(req);
        if (deductResult == null || deductResult.getCode() != 200) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Deduct quota failed: " + (deductResult != null ? deductResult.getMessage() : "unknown"));
        }
        
        return new SignContractResult("SUCCESS", null);
    }

    @Override
    public ContractLinkResult getContractLink(Long userId, Long applicationId) {
        // TODO: 后续替换为从 OSS 获取真实的合同下载链接或预览 Token
        return new ContractLinkResult("https://oss.crediflow.com/contracts/dummy.pdf");
    }

    @Transactional(rollbackFor = Exception.class)
    public void generateReceiptAndPlan(Long applicationId, Long userId, BigDecimal amount, Integer term) {
        // 1. 生成借据
        LoanReceipt receipt = new LoanReceipt();
        receipt.setReceiptNo("REC" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        receipt.setApplicationId(applicationId);
        receipt.setUserId(userId);
        receipt.setPrincipalAmount(amount);
        receipt.setAnnualInterestRate(new BigDecimal(annualRateStr)); // dynamic annual rate
        receipt.setTotalTerms(term);
        receipt.setStatus("ACTIVE");
        receipt.setCreatedAt(new Date());
        receipt.setUpdatedAt(new Date());
        loanReceiptMapper.insert(receipt);

        // 2. 拆分还款计划 (简单等额本金示例)
        BigDecimal principalPerTerm = amount.divide(new BigDecimal(term), 2, RoundingMode.HALF_UP);
        BigDecimal monthlyInterestRate = receipt.getAnnualInterestRate().divide(new BigDecimal(12), 4, RoundingMode.HALF_UP);
        
        BigDecimal remainingPrincipal = amount;
        Calendar cal = Calendar.getInstance();
        
        for (int i = 1; i <= term; i++) {
            RepaymentPlan plan = new RepaymentPlan();
            plan.setReceiptId(receipt.getId());
            plan.setUserId(userId);
            plan.setTermNo(i);
            
            BigDecimal currentPrincipal = (i == term) ? remainingPrincipal : principalPerTerm;
            BigDecimal currentInterest = remainingPrincipal.multiply(monthlyInterestRate).setScale(2, RoundingMode.HALF_UP);
            
            plan.setPrincipalAmount(currentPrincipal);
            plan.setInterestAmount(currentInterest);
            
            cal.add(Calendar.MONTH, 1);
            plan.setDueDate(cal.getTime());
            plan.setStatus("PENDING");
            plan.setCreatedAt(new Date());
            plan.setUpdatedAt(new Date());
            repaymentPlanMapper.insert(plan);
            
            remainingPrincipal = remainingPrincipal.subtract(currentPrincipal);
        }
    }

    @Override
    public CreditContractStatusResult getLatestCreditContractStatus(Long userId) {
        LambdaQueryWrapper<LoanContract> query = new LambdaQueryWrapper<>();
        query.eq(LoanContract::getUserId, userId)
             .eq(LoanContract::getContractType, "CREDIT_CONTRACT")
             .orderByDesc(LoanContract::getCreatedAt)
             .last("LIMIT 1");
             
        LoanContract contract = this.getOne(query);
        if (contract != null) {
            return new CreditContractStatusResult(contract.getStatus(), contract.getContractNo());
        }
        return new CreditContractStatusResult("NOT_FOUND", null);
    }
}
