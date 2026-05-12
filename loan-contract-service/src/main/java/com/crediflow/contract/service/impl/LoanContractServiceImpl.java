package com.crediflow.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.contract.entity.LoanContract;
import com.crediflow.contract.mapper.LoanContractMapper;
import com.crediflow.contract.service.LoanContractService;
import com.crediflow.contract.util.PdfGeneratorUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class LoanContractServiceImpl extends ServiceImpl<LoanContractMapper, LoanContract> implements LoanContractService {

    @Autowired
    private PdfGeneratorUtil pdfGeneratorUtil;

    @Override
    public Map<String, Object> signAndGenerateContract(Long userId, Long applicationId, BigDecimal amount, Integer term, boolean agreed) {
        if (!agreed) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "必须阅读并同意《个人借款协议》《征信授权书》");
        }

        // 1. 生成合同号
        String contractNo = "CTR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);

        // 2. 生成 PDF
        String pdfLink = pdfGeneratorUtil.generateAndStoreContractPdf(contractNo, userId, amount.toString());

        // 3. 落库
        LoanContract contract = new LoanContract();
        contract.setContractNo(contractNo);
        contract.setApplicationId(applicationId);
        contract.setUserId(userId);
        contract.setLoanAmount(amount);
        contract.setInterestRate(new BigDecimal("0.120000")); // 写死12%用于演示
        contract.setTerm(term);
        contract.setStatus("EFFECTIVE");
        contract.setSignTime(new Date());
        contract.setCreatedAt(new Date());
        contract.setUpdatedAt(new Date());
        
        this.save(contract);

        // 4. 模拟审计日志与流水入库记录（最简合规）
        System.out.println("AUDIT_LOG: userId=" + userId + " agreed to contract " + contractNo + ". IP recorded. PDF Link: " + pdfLink);
        
        return Map.of(
            "contractNo", contractNo,
            "status", "EFFECTIVE",
            "pdfLink", pdfLink
        );
    }

    @Override
    public Map<String, Object> getContractLink(Long userId, Long applicationId) {
        LoanContract contract = this.getOne(new LambdaQueryWrapper<LoanContract>()
                .eq(LoanContract::getUserId, userId)
                .eq(LoanContract::getApplicationId, applicationId)
                .last("LIMIT 1"));
        
        if (contract == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "未找到相应的合同记录");
        }

        // 模拟返回拼接的 PDF 链接，并且记录调阅审计日志
        String fileName = contract.getContractNo() + ".pdf";
        String pdfLink = "http://localhost:8080/files/contracts/" + fileName;

        System.out.println("AUDIT_LOG: userId=" + userId + " queried contract PDF link for " + contract.getContractNo());

        return Map.of(
            "contractNo", contract.getContractNo(),
            "pdfLink", pdfLink,
            "signTime", contract.getSignTime()
        );
    }
}
