package com.crediflow.application.feign;

import com.crediflow.common.api.user.UserEligibilityResponse;
import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/internal/user/eligibility")
    Result<UserEligibilityResponse> getEligibility(@RequestParam("userId") Long userId);

    @GetMapping("/api/internal/user/by-phone")
    Result<Long> getUserIdByPhone(@RequestParam("phone") String phone);
}
