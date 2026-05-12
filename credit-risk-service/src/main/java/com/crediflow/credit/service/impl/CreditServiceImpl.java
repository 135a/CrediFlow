package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.feign.AgentClient;
import com.crediflow.credit.mapper.CreditResultMapper;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Service
public class CreditServiceImpl extends ServiceImpl<CreditResultMapper, CreditResult> implements CreditService {

    @Autowired
    private AgentClient agentClient;

    @Override
    public CreditResult applyCredit(Long userId) {
        // 1. 检查是否已有活跃授信
        CreditResult existing = getActiveCredit(userId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户已存在有效授信，无需重复申请");
        }

        // 2. 构造风控数据，调用大模型 Agent（或降级）
        Map<String, Object> userData = Map.of("userId", userId, "action", "evaluate");
        Map<String, Object> riskResult = agentClient.evaluateRisk(userData);

        // 3. 解析结果并落库
        BigDecimal suggestedAmount = riskResult.get("suggestedAmount") != null 
                ? new BigDecimal(riskResult.get("suggestedAmount").toString()) 
                : new BigDecimal("5000.00");

        CreditResult result = new CreditResult();
        result.setUserId(userId);
        result.setCreditAmount(suggestedAmount);
        result.setUsedAmount(BigDecimal.ZERO);
        result.setStatus("ACTIVE");
        // 设置有效期为 30 天
        result.setExpireTime(new Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000));
        result.setCreatedAt(new Date());
        result.setUpdatedAt(new Date());

        this.save(result);
        return result;
    }

    @Override
    public CreditResult getActiveCredit(Long userId) {
        return this.getOne(new LambdaQueryWrapper<CreditResult>()
                .eq(CreditResult::getUserId, userId)
                .eq(CreditResult::getStatus, "ACTIVE")
                .gt(CreditResult::getExpireTime, new Date())
                .last("LIMIT 1"));
    }
}
