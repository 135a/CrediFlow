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
@Slf4j // 使用@Slf4j注解，用于日志记录
@RestController // 表明这是一个RESTful控制器
@RequestMapping("/api/internal/risk") // 定义基础请求路径
public class RiskBlacklistInternalController { // 风控服务黑名单内部控制器类



    /**
     * 检查黑名单接口
     * @param idCardFingerprint 身份证指纹信息
     * @return 返回检查结果，当前为桩实现，始终返回未命中黑名单
     */
    @PostMapping("/blacklist/check") // 定义POST请求路径
    public Result<BlacklistCheckResult> check(@RequestParam("idCardFingerprint") String idCardFingerprint) { // 检查方法
        // 参数校验：检查身份证指纹是否为空或空白
        if (idCardFingerprint == null || idCardFingerprint.isBlank()) {
            return Result.error(400, "idCardFingerprint required", null); // 返回错误信息
        }
        // 记录日志，对指纹信息进行脱敏处理
        log.info("[risk-blacklist] stub-check fingerprint={}", maskFingerprint(idCardFingerprint));
        // 返回成功结果，hit设为false表示未命中黑名单
        return Result.success(new BlacklistCheckResult(false, null));
    }



    /**
     * 对指纹信息进行脱敏处理
     * @param fp 原始指纹信息
     * @return 脱敏后的指纹信息
     */
    private static String maskFingerprint(String fp) { // 静态私有方法，用于指纹脱敏
        // 如果指纹长度大于8，则显示前8位加***，否则显示****
        return fp.length() > 8 ? fp.substring(0, 8) + "***" : "****";
    }
}
