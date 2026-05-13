package com.crediflow.user.eligibility;

import com.crediflow.user.eligibility.model.EligibilityOutcome;

/**
 * KYC step1 前置：年龄、唯一性、黑名单、限流聚合（实现类后续 Phase 2 提供）。
 */
public interface EligibilityChecker {

    EligibilityOutcome check(long userId, String realName, String idCardNo);
}
