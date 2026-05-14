package com.crediflow.contract.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.contract.entity.LoanContract;
import com.crediflow.contract.mapper.LoanContractMapper;
import com.crediflow.contract.service.LoanContractService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class LoanContractServiceImpl extends ServiceImpl<LoanContractMapper, LoanContract> implements LoanContractService {

    @Override
    public void generateContract(Long applicationId, Long userId, String contractType) {
        LoanContract contract = new LoanContract();
        contract.setContractNo("CTR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        contract.setApplicationId(applicationId);
        contract.setUserId(userId);
        contract.setContractType(contractType);
        contract.setStatus("INIT"); // 初始生成，等待签署
        contract.setContractUrl("https://oss.crediflow.com/contracts/" + contractType + "/" + contract.getContractNo() + ".pdf");
        contract.setCreatedAt(new Date());
        contract.setUpdatedAt(new Date());
        this.save(contract);
    }

    @Override
    public java.util.Map<String, Object> signAndGenerateContract(Long userId, Long applicationId, java.math.BigDecimal amount, Integer term, boolean agreed) {
        if (!agreed) {
            throw new RuntimeException("必须同意协议才能签约");
        }
        
        // Find existing INIT contract
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LoanContract> query = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(LoanContract::getApplicationId, applicationId)
             .eq(LoanContract::getUserId, userId)
             .eq(LoanContract::getStatus, "INIT")
             .last("LIMIT 1");
             
        LoanContract contract = this.getOne(query);
        if (contract == null) {
            generateContract(applicationId, userId, "LOAN_CONTRACT");
        } else {
            contract.setStatus("SIGNED");
            contract.setUpdatedAt(new Date());
            this.updateById(contract);
        }
        
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("status", "SUCCESS");
        return map;
    }

    @Override
    public java.util.Map<String, Object> getContractLink(Long userId, Long applicationId) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("link", "https://oss.crediflow.com/contracts/dummy.pdf");
        return map;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.crediflow.contract.mapper.LoanReceiptMapper loanReceiptMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.crediflow.contract.mapper.RepaymentPlanMapper repaymentPlanMapper;

    @org.springframework.beans.factory.annotation.Value("${crediflow.loan.rate.annual:0.18}")
    private String annualRateStr;

    @org.springframework.transaction.annotation.Transactional
    public void generateReceiptAndPlan(Long applicationId, Long userId, java.math.BigDecimal amount, Integer term) {
        // 1. 生成借据
        com.crediflow.contract.entity.LoanReceipt receipt = new com.crediflow.contract.entity.LoanReceipt();
        receipt.setReceiptNo("REC" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        receipt.setApplicationId(applicationId);
        receipt.setUserId(userId);
        receipt.setPrincipalAmount(amount);
        receipt.setAnnualInterestRate(new java.math.BigDecimal(annualRateStr)); // dynamic annual rate
        receipt.setTotalTerms(term);
        receipt.setStatus("ACTIVE");
        receipt.setCreatedAt(new Date());
        receipt.setUpdatedAt(new Date());
        loanReceiptMapper.insert(receipt);

        // 2. 拆分还款计划 (简单等额本金示例)
        java.math.BigDecimal principalPerTerm = amount.divide(new java.math.BigDecimal(term), 2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal monthlyInterestRate = receipt.getAnnualInterestRate().divide(new java.math.BigDecimal(12), 4, java.math.RoundingMode.HALF_UP);
        
        java.math.BigDecimal remainingPrincipal = amount;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        
        for (int i = 1; i <= term; i++) {
            com.crediflow.contract.entity.RepaymentPlan plan = new com.crediflow.contract.entity.RepaymentPlan();
            plan.setReceiptId(receipt.getId());
            plan.setUserId(userId);
            plan.setTermNo(i);
            
            java.math.BigDecimal currentPrincipal = (i == term) ? remainingPrincipal : principalPerTerm;
            java.math.BigDecimal currentInterest = remainingPrincipal.multiply(monthlyInterestRate).setScale(2, java.math.RoundingMode.HALF_UP);
            
            plan.setPrincipalAmount(currentPrincipal);
            plan.setInterestAmount(currentInterest);
            
            cal.add(java.util.Calendar.MONTH, 1);
            plan.setDueDate(cal.getTime());
            plan.setStatus("PENDING");
            plan.setCreatedAt(new Date());
            plan.setUpdatedAt(new Date());
            repaymentPlanMapper.insert(plan);
            
            remainingPrincipal = remainingPrincipal.subtract(currentPrincipal);
        }
    }
}
