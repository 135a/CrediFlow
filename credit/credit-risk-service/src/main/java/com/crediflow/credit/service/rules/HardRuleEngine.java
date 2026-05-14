package com.crediflow.credit.service.rules;

public interface HardRuleEngine {
    /**
     * 执行硬规则校验（黑名单、逾期、账号状态等）
     * @param userId 用户ID
     * @return 校验结果
     */
    HardRuleResult evaluate(Long userId);
}
