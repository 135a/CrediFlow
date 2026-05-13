package com.crediflow.user.eligibility.feign;

import com.crediflow.common.web.Result;
import com.crediflow.user.eligibility.feign.dto.BlacklistCheckRequest;
import com.crediflow.user.eligibility.feign.dto.BlacklistCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 风控服务黑名单二次校验入口。
 * <p>仅传 {@code idCardFingerprint}，跨服务 MUST NOT 出现明文姓名 / 身份证号。</p>
 */
@FeignClient(name = "credit-risk-service", contextId = "kycRiskBlacklistClient",
        fallback = RiskBlacklistClientFallback.class)
public interface RiskBlacklistClient {

    @PostMapping("/api/internal/risk/blacklist/check")
    Result<BlacklistCheckResponse> check(@RequestBody BlacklistCheckRequest request);
}
