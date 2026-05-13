package com.crediflow.user.eligibility;

import com.crediflow.common.kyc.IdCardFingerprintCalculator;
import com.crediflow.user.eligibility.model.EligibilityDecision;
import com.crediflow.user.eligibility.model.EligibilityOutcome;
import com.crediflow.user.eligibility.policy.AgeRangePolicy;
import com.crediflow.user.eligibility.policy.BlacklistPolicy;
import com.crediflow.user.eligibility.policy.EligibilityRateLimiter;
import com.crediflow.user.eligibility.policy.IdCardUniquenessPolicy;
import com.crediflow.user.realname.config.RealnameProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 闸门聚合：限流 → 年龄 → 唯一性 → 黑名单。任一未过立即返回；MUST NOT 触发外部 Provider。
 */
@Component
public class DefaultEligibilityChecker implements EligibilityChecker {

    private final EligibilityRateLimiter rateLimiter;
    private final AgeRangePolicy agePolicy;
    private final IdCardUniquenessPolicy uniquenessPolicy;
    private final BlacklistPolicy blacklistPolicy;
    private final RealnameProperties realnameProperties;

    public DefaultEligibilityChecker(EligibilityRateLimiter rateLimiter,
                                     AgeRangePolicy agePolicy,
                                     IdCardUniquenessPolicy uniquenessPolicy,
                                     BlacklistPolicy blacklistPolicy,
                                     RealnameProperties realnameProperties) {
        this.rateLimiter = rateLimiter;
        this.agePolicy = agePolicy;
        this.uniquenessPolicy = uniquenessPolicy;
        this.blacklistPolicy = blacklistPolicy;
        this.realnameProperties = realnameProperties;
    }

    @Override
    public EligibilityOutcome check(long userId, String realName, String idCardNo) {
        if (!rateLimiter.tryAcquire(userId)) {
            return EligibilityOutcome.reject(EligibilityDecision.REJECT_RATE_LIMIT, "RATE_LIMIT");
        }
        AgeRangePolicy.Result age = agePolicy.evaluate(idCardNo, LocalDate.now());
        if (!age.pass()) {
            return EligibilityOutcome.reject(EligibilityDecision.REJECT_AGE, "AGE_" + age.age());
        }
        String fingerprint = IdCardFingerprintCalculator.hmacSha256Hex(
                realnameProperties.getIdempotencySalt(), realName, idCardNo);
        if (!uniquenessPolicy.isUnique(userId, fingerprint)) {
            return EligibilityOutcome.reject(EligibilityDecision.REJECT_DUP, "DUP");
        }
        BlacklistPolicy.Decision decision = blacklistPolicy.check(fingerprint);
        if (decision.hit()) {
            return EligibilityOutcome.reject(EligibilityDecision.REJECT_BLACKLIST, decision.reasonCode());
        }
        return EligibilityOutcome.pass();
    }
}
