package com.crediflow.credit.service.rules;

import lombok.Data;

@Data
public class HardRuleResult {
    private boolean passed;
    private String rejectCode;
    private String auditDetail;
    
    public static HardRuleResult pass() {
        HardRuleResult result = new HardRuleResult();
        result.setPassed(true);
        return result;
    }
    
    public static HardRuleResult reject(String code, String detail) {
        HardRuleResult result = new HardRuleResult();
        result.setPassed(false);
        result.setRejectCode(code);
        result.setAuditDetail(detail);
        return result;
    }
}
