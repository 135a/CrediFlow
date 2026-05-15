package com.crediflow.credit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.credit.entity.CreditResult;

public interface CreditService extends IService<CreditResult> {
    com.crediflow.credit.entity.CreditApplication applyCredit(Long userId);
    CreditResult getActiveCredit(Long userId);
    void manualApprove(Long applicationId, String remark);
    
    /**
     * 生成或更新用户授信额度
     */
    void generateUserQuota(Long userId, double totalScore);

    /**
     * 高并发安全的额度扣减接口（基于乐观锁）
     */
    void deductQuota(Long userId, java.math.BigDecimal amount);
    
    java.util.Map<String, Object> getQuotaSummary(Long userId);
    void escalateRiskSignal(java.util.Map<String, Object> signalData);
    String evaluateLoanRisk(java.util.Map<String, Object> req);
    void enqueueLoanReview(java.util.Map<String, Object> req);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.crediflow.credit.entity.CreditReviewQueue> listReviewQueue(long current, long size);
}
