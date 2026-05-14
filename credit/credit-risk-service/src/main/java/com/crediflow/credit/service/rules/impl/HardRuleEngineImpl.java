package com.crediflow.credit.service.rules.impl;

import com.crediflow.credit.service.rules.HardRuleEngine;
import com.crediflow.credit.service.rules.HardRuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HardRuleEngineImpl implements HardRuleEngine {

    @Override
    public HardRuleResult evaluate(Long userId) {
        if (userId == null) {
            return HardRuleResult.reject("INVALID_USER", "User ID cannot be null");
        }
        
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
