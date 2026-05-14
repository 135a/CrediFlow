package com.crediflow.user.eligibility.model;

/**
 * 准入闸门判定结果（内部码禁止直接透出到 API）。
 */
public record EligibilityOutcome(EligibilityDecision decision, String internalCode) {

    public static EligibilityOutcome pass() {
        return new EligibilityOutcome(EligibilityDecision.PASS, null);
    }

    public static EligibilityOutcome reject(EligibilityDecision decision, String internalCode) {
        return new EligibilityOutcome(decision, internalCode);
    }
}
