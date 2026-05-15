package com.crediflow.user.eligibility.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.user.eligibility.config.KycEligibilityProperties;
import com.crediflow.user.eligibility.entity.IdCardBlacklist;
import com.crediflow.user.eligibility.feign.RiskBlacklistClient;
import com.crediflow.user.eligibility.feign.dto.BlacklistCheckResponse;
import com.crediflow.user.eligibility.mapper.IdCardBlacklistMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 黑名单双层校验：先本地表，未命中再调风控服务；风控异常 MUST NOT 默认放行。
 */
@Component
public class BlacklistPolicy {

    private static final Logger log = LoggerFactory.getLogger(BlacklistPolicy.class);

    private final IdCardBlacklistMapper localMapper;
    private final RiskBlacklistClient riskClient;
    private final KycEligibilityProperties properties;

    public BlacklistPolicy(IdCardBlacklistMapper localMapper,
                           RiskBlacklistClient riskClient,
                           KycEligibilityProperties properties) {
        this.localMapper = localMapper;
        this.riskClient = riskClient;
        this.properties = properties;
    }

    public Decision check(String idCardFingerprint) {
        if (idCardFingerprint == null || idCardFingerprint.isBlank()) {
            return Decision.pass();
        }
        IdCardBlacklist hit = localMapper.selectOne(new LambdaQueryWrapper<IdCardBlacklist>()
                .eq(IdCardBlacklist::getIdCardFingerprint, idCardFingerprint));
        if (hit != null) {
            return Decision.hit("LOCAL_BLACKLIST");
        }
        if (!properties.isRiskServiceEnabled()) {
            return Decision.pass();
        }
        Result<BlacklistCheckResponse> resp;
        try {
            resp = riskClient.check(idCardFingerprint);
        } catch (Exception e) {
            log.warn("[eligibility] risk blacklist upstream error: {}", e.toString());
            throw new BusinessException(ErrorCode.KYC_RISK_UPSTREAM_UNAVAILABLE,
                    ErrorCode.KYC_RISK_UPSTREAM_UNAVAILABLE.getMessage());
        }
        if (resp == null || resp.getCode() == null || resp.getCode() != ErrorCode.SUCCESS.getCode()
                || resp.getData() == null) {
            throw new BusinessException(ErrorCode.KYC_RISK_UPSTREAM_UNAVAILABLE,
                    ErrorCode.KYC_RISK_UPSTREAM_UNAVAILABLE.getMessage());
        }
        if (resp.getData().isHit()) {
            String reason = resp.getData().getReasonCode();
            return Decision.hit(reason == null || reason.isBlank() ? "RISK_SERVICE" : reason);
        }
        return Decision.pass();
    }

    public record Decision(boolean hit, String reasonCode) {

        public static Decision pass() {
            return new Decision(false, null);
        }

        public static Decision hit(String reasonCode) {
            return new Decision(true, reasonCode);
        }
    }
}
