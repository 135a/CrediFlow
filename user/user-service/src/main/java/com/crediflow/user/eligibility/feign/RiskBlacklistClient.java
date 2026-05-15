package com.crediflow.user.eligibility.feign;

import com.crediflow.common.web.Result;
import com.crediflow.user.eligibility.feign.dto.BlacklistCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 风控服务黑名单二次校验；仅传 {@code idCardFingerprint}。
 */
@FeignClient(name = "credit-risk-service", contextId = "kycRiskBlacklistClient",
        fallback = RiskBlacklistClientFallback.class)
public interface RiskBlacklistClient {

    @PostMapping("/api/internal/risk/blacklist/check")
    Result<BlacklistCheckResponse> check(@RequestParam("idCardFingerprint") String idCardFingerprint);
}
