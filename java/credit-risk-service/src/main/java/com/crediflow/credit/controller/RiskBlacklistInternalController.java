package com.crediflow.credit.controller;

import com.crediflow.common.auth.annotation.Inner;
import com.crediflow.common.web.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 风控服务黑名单内部接口（OpenSpec：kyc-eligibility-gate / internal-api-security）。
 *
 * <p>当前为接入桩：始终返回 hit=false，等专业风控规则上线后切换实现。
 * 入参 MUST 仅含 {@code idCardFingerprint}，跨服务严禁透传明文。</p>
 */
@RestController
@RequestMapping("/api/internal/risk")
public class RiskBlacklistInternalController {

    private static final Logger log = LoggerFactory.getLogger(RiskBlacklistInternalController.class);

    @Inner
    @PostMapping("/blacklist/check")
    public Result<Map<String, Object>> check(@RequestBody Map<String, String> body) {
        String fp = body == null ? null : body.get("idCardFingerprint");
        if (fp == null || fp.isBlank()) {
            return Result.error(400, "idCardFingerprint required", null);
        }
        log.info("[risk-blacklist] stub-check fingerprint={}", fp.length() > 8 ? fp.substring(0, 8) + "***" : "****");
        Map<String, Object> resp = new HashMap<>();
        resp.put("hit", false);
        resp.put("reasonCode", null);
        return Result.success(resp);
    }
}
