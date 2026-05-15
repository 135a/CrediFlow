package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import com.crediflow.credit.dto.BlacklistCheckResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控服务黑名单内部接口（OpenSpec：kyc-eligibility-gate / internal-api-security）。
 *
 * <p>当前为接入桩：始终返回 hit=false。入参仅 {@code idCardFingerprint}，跨服务严禁透传明文。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/risk")
public class RiskBlacklistInternalController {

    @PostMapping("/blacklist/check")
    public Result<BlacklistCheckResult> check(@RequestParam("idCardFingerprint") String idCardFingerprint) {
        if (idCardFingerprint == null || idCardFingerprint.isBlank()) {
            return Result.error(400, "idCardFingerprint required", null);
        }
        log.info("[risk-blacklist] stub-check fingerprint={}", maskFingerprint(idCardFingerprint));
        return Result.success(new BlacklistCheckResult(false, null));
    }

    private static String maskFingerprint(String fp) {
        return fp.length() > 8 ? fp.substring(0, 8) + "***" : "****";
    }
}
