package com.crediflow.application.feign;

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

    /** KYC v2 + 主卡前置查询。 */
    @GetMapping("/api/internal/user/eligibility")
    Result<Map<String, Object>> getEligibility(@RequestParam("userId") Long userId);
}
