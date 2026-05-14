package com.crediflow.credit.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/api/app/user/kyc/status")
    Result<Map<String, Object>> getKycStatus(@RequestParam("userId") Long userId);

    @GetMapping("/api/internal/user/by-phone")
    Result<Long> getUserIdByPhone(@RequestParam("phone") String phone);

    /**
     * KYC v2 受理前置：返回 {@code kycPassed} 与 {@code hasPrimaryBankCard}。
     */
    @GetMapping("/api/internal/user/eligibility")
    Result<Map<String, Object>> getEligibility(@RequestParam("userId") Long userId);
    
    /**
     * 发起二次人脸核验请求
     * @param userId 用户ID
     * @param bizScene 业务场景，如 CREDIT_SECONDARY_FACE
     * @param callbackUrl 回调URL
     * @return 业务流水号或token
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/internal/user/face/init")
    Result<String> initFaceLiveness(@RequestParam("userId") Long userId, 
                                    @RequestParam("bizScene") String bizScene,
                                    @RequestParam("callbackUrl") String callbackUrl);
}
