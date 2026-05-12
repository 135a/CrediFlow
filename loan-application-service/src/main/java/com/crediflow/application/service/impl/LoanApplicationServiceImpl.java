package com.crediflow.application.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.application.entity.LoanApplication;
import com.crediflow.application.feign.CreditClient;
import com.crediflow.application.mapper.LoanApplicationMapper;
import com.crediflow.application.service.LoanApplicationService;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.crediflow.application.feign.UserClient;

@Service
public class LoanApplicationServiceImpl extends ServiceImpl<LoanApplicationMapper, LoanApplication> implements LoanApplicationService {

    @Autowired
    private CreditClient creditClient;

    @Autowired
    private UserClient userClient;

    @Override
    public LoanApplication applyLoan(Long userId, BigDecimal applyAmount, Integer term, String idmpToken) {
        // 0. KYC 校验
        Result<Map<String, Object>> kycResult = userClient.getKycStatus(userId);
        if (kycResult == null || kycResult.getData() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "尚未通过kyc认证");
        }
        Map<String, Object> kycData = kycResult.getData();
        Integer stepStatus = (Integer) kycData.get("stepStatus");
        if (stepStatus == null || stepStatus < 3) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "尚未通过kyc认证");
        }

        // 1. 幂等校验（实际应使用 Redis + Redisson 获取锁并存储 idmpToken，这里简写业务逻辑）
        // boolean locked = redisTemplate.opsForValue().setIfAbsent("IDMP:LOAN:" + idmpToken, userId, 10, TimeUnit.MINUTES);
        // if (!locked) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请勿重复提交申请");

        // 2. 联合校验：检查有效授信额度
        Result<Map<String, Object>> creditResult = creditClient.getActiveCreditInternal(userId);
        if (creditResult == null || creditResult.getCode() != 200 || creditResult.getData() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无有效授信，请先申请额度");
        }

        Map<String, Object> creditData = creditResult.getData();
        BigDecimal creditAmount = new BigDecimal(creditData.get("creditAmount").toString());
        BigDecimal usedAmount = new BigDecimal(creditData.get("usedAmount").toString());
        BigDecimal availableAmount = creditAmount.subtract(usedAmount);

        if (applyAmount.compareTo(availableAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请金额超过可用额度");
        }

        // 3. 状态机：创建申请单 (PENDING)
        LoanApplication application = new LoanApplication();
        application.setApplicationNo("LOAN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        application.setUserId(userId);
        application.setApplyAmount(applyAmount);
        application.setTerm(term);
        application.setStatus("PENDING");
        application.setCreatedAt(new Date());
        application.setUpdatedAt(new Date());

        this.save(application);
        return application;
    }

    @Override
    public void approve(Long applicationId) {
        LoanApplication application = this.getById(applicationId);
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请单状态非法，无法通过");
        }
        application.setStatus("APPROVED");
        this.updateById(application);
        // TODO: 触发合同生成事件或同步调用合同服务
    }

    @Override
    public void reject(Long applicationId, String reason) {
        LoanApplication application = this.getById(applicationId);
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请单状态非法，无法拒绝");
        }
        application.setStatus("REJECTED");
        this.updateById(application);
    }
}
