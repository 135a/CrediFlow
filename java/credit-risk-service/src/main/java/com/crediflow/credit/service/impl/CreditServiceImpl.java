package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.feign.AgentClient;
import com.crediflow.credit.mapper.CreditResultMapper;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import com.crediflow.credit.feign.UserClient;

@Service
public class CreditServiceImpl extends ServiceImpl<CreditResultMapper, CreditResult> implements CreditService {

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private UserClient userClient;

    @Autowired
    private com.crediflow.credit.service.CreditApplicationService creditApplicationService;

    @Override
    public com.crediflow.credit.entity.CreditApplication applyCredit(Long userId) {
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
        // 实名二要素（openspec: realname-thirdparty-http-backend / tasks 7.2）
        Object realnameStatus = kycData.get("realnameStatus");
        if (!"VERIFIED".equals(realnameStatus != null ? realnameStatus.toString() : null)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "尚未完成实名核验");
        }

        // 1. 检查是否已有活跃授信
        CreditResult existing = getActiveCredit(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户已存在有效授信，无需重复申请");
        }

        // 2. 先插入 PENDING 状态的申请记录
        com.crediflow.credit.entity.CreditApplication application = new com.crediflow.credit.entity.CreditApplication();
        application.setUserId(userId);
        application.setApplyAmount(new BigDecimal("5000.00")); // 默认请求额度
        application.setStatus("PENDING");
        application.setCreatedAt(new Date());
        application.setUpdatedAt(new Date());
        creditApplicationService.save(application);

        // 3. 构造风控数据，调用大模型 Agent（或降级）
        // 传递 KYC 的真实年龄、月收入等给 Agent
        try {
            Map<String, Object> userData = Map.of(
                "userId", userId,
                "action", "evaluate",
                "age", kycData.get("age") != null ? kycData.get("age") : 22,
                "income", kycData.get("monthlyIncome") != null ? kycData.get("monthlyIncome") : 5000,
                "occupation", kycData.get("occupation") != null ? kycData.get("occupation") : "UNKNOWN"
            );
            Map<String, Object> riskResult = agentClient.evaluateRisk(userData);

            String agentStatus = riskResult.get("status") != null ? riskResult.get("status").toString() : "UNKNOWN";
            String reason = riskResult.get("reason") != null ? riskResult.get("reason").toString() : "";

            if ("REJECT".equals(agentStatus)) {
                application.setStatus("REJECTED");
                application.setAuditReason(reason);
                application.setUpdatedAt(new Date());
                creditApplicationService.updateById(application);
            } else {
                BigDecimal suggestedAmount = riskResult.get("suggestedAmount") != null 
                        ? new BigDecimal(riskResult.get("suggestedAmount").toString()) 
                        : new BigDecimal("5000.00");
                
                application.setStatus("APPROVED");
                application.setSuggestedAmount(suggestedAmount);
                application.setAuditReason(reason);
                application.setUpdatedAt(new Date());
                creditApplicationService.updateById(application);

                // 生成最终额度
                CreditResult result = new CreditResult();
                result.setUserId(userId);
                result.setCreditAmount(suggestedAmount);
                result.setUsedAmount(BigDecimal.ZERO);
                result.setStatus("ACTIVE");
                result.setExpireTime(new Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000));
                result.setCreatedAt(new Date());
                result.setUpdatedAt(new Date());
                this.save(result);
            }
        } catch (Exception e) {
            // Agent调用失败，保持 PENDING 状态，记录错误信息以便排查
            application.setAuditReason("风控评估异常: " + e.getMessage());
            creditApplicationService.updateById(application);
        }

        return application;
    }

    @Override
    public CreditResult getActiveCredit(Long userId) {
        return this.getOne(new LambdaQueryWrapper<CreditResult>()
                .eq(CreditResult::getUserId, userId)
                .eq(CreditResult::getStatus, "ACTIVE")
                .gt(CreditResult::getExpireTime, new Date())
                .last("LIMIT 1"));
    }

    @Override
    public void manualApprove(Long applicationId, String remark) {
        com.crediflow.credit.entity.CreditApplication application = creditApplicationService.getById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "申请记录不存在");
        }
        if (!"REJECTED".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅允许对已拒绝的申请进行干预");
        }

        // 修改状态为通过
        application.setStatus("APPROVED");
        application.setAuditReason(application.getAuditReason() + " | [人工审核强制通过]: " + remark);
        application.setUpdatedAt(new Date());
        creditApplicationService.updateById(application);

        // 生成最终额度
        CreditResult result = new CreditResult();
        result.setUserId(application.getUserId());
        result.setCreditAmount(application.getSuggestedAmount() != null ? application.getSuggestedAmount() : new BigDecimal("5000.00"));
        result.setUsedAmount(BigDecimal.ZERO);
        result.setStatus("ACTIVE");
        result.setExpireTime(new Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000));
        result.setCreatedAt(new Date());
        result.setUpdatedAt(new Date());
        this.save(result);
    }
}
