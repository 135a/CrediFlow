package com.crediflow.user.eligibility.policy;

import com.crediflow.user.realname.support.RealnameRateLimiter;
import org.springframework.stereotype.Component;

/**
 * KYC 闸门限流 facade。底层直接复用 {@link RealnameRateLimiter} 的实现（窗口/阈值配置同源），
 * 这里仅做语义上的隔离，避免下游误以为是另一份独立计数。
 */
@Component
public class EligibilityRateLimiter {

    private final RealnameRateLimiter delegate;

    public EligibilityRateLimiter(RealnameRateLimiter delegate) {
        this.delegate = delegate;
    }

    public boolean tryAcquire(long userId) {
        return delegate.tryAcquire(userId);
    }
}
