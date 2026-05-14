package com.crediflow.user.eligibility.feign;

import com.crediflow.common.web.Result;
import com.crediflow.user.eligibility.feign.dto.BlacklistCheckRequest;
import com.crediflow.user.eligibility.feign.dto.BlacklistCheckResponse;
import org.springframework.stereotype.Component;

/**
 * 风控不可用时 MUST NOT 默认放行；以业务错误码暴露给上层（500 由 BlacklistPolicy 翻译）。
 */
@Component
class RiskBlacklistClientFallback implements RiskBlacklistClient {

    @Override
    public Result<BlacklistCheckResponse> check(BlacklistCheckRequest request) {
        return Result.error(500, "RISK_UPSTREAM_UNAVAILABLE", null);
    }
}
