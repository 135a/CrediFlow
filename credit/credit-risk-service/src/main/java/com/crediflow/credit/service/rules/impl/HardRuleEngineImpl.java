package com.crediflow.credit.service.rules.impl;

import com.crediflow.credit.service.rules.HardRuleEngine;
import com.crediflow.credit.service.rules.HardRuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 硬规则引擎实现类
 * 使用@Slf4j注解提供日志支持
 * 使用@Service注解标记为Spring服务组件
 */
@Slf4j
@Service
public class HardRuleEngineImpl implements HardRuleEngine {

    /**
     * 评估用户是否符合硬规则
     * @param userId 用户ID
     * @return HardRuleResult 评估结果，包含通过/拒绝状态及相关信息
     */
    @Override
    public HardRuleResult evaluate(Long userId) {
        // 检查用户ID是否为空
        if (userId == null) {
            return HardRuleResult.reject("INVALID_USER", "User ID cannot be null");
        }
        
        // 记录评估硬规则的开始日志
        log.info("Evaluating hard rules for user: {}", userId);
        
        // TODO: Call user-service/loan-service to check blacklist, overdue, account status
        // Mock hard rules (Blacklist / Overdue / Account Status)
        if (userId == 999L) {
            log.warn("User {} hit internal blacklist", userId);
            return HardRuleResult.reject("BLACKLISTED", "Hit internal blacklist");
        }
        if (userId == 888L) {
            log.warn("User {} has unsettled overdue loans", userId);
            return HardRuleResult.reject("HAS_OVERDUE", "Unsettled overdue loans exist");
        }
        if (userId == 777L) {
            log.warn("User {} account status is abnormal", userId);
            return HardRuleResult.reject("ABNORMAL_ACCOUNT", "Account status is locked or abnormal");
        }
        
        log.info("User {} passed all hard rules", userId);
        return HardRuleResult.pass();
    }
}
