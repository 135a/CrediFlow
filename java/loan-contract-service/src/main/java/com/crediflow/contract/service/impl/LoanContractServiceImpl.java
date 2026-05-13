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
    public void generateContract(Long applicationId, Long userId) {
        LoanContract contract = new LoanContract();
        contract.setContractNo("CTR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        contract.setApplicationId(applicationId);
        contract.setUserId(userId);
        contract.setStatus("GENERATED");
        contract.setContractUrl("https://oss.crediflow.com/contracts/" + contract.getContractNo() + ".pdf");
        contract.setCreatedAt(new Date());
        contract.setUpdatedAt(new Date());
        this.save(contract);
    }

    @Override
    public java.util.Map<String, Object> signAndGenerateContract(Long userId, Long applicationId, java.math.BigDecimal amount, Integer term, boolean agreed) {
        if (!agreed) {
            throw new RuntimeException("必须同意协议才能签约");
        }
        generateContract(applicationId, userId);
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
}
