package com.crediflow.user.eligibility.model;

public enum EligibilityDecision {
    PASS,
    REJECT_AGE,
    REJECT_DUP,
    REJECT_BLACKLIST,
    REJECT_RATE_LIMIT
}
