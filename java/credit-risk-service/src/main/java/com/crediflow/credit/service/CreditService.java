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
}
