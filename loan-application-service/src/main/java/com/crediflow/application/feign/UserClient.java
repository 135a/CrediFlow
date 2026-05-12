package com.crediflow.application.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "user-service", url = "${user.url:http://localhost:8081}")
public interface UserClient {
    @GetMapping("/api/app/user/kyc/status")
    Result<Map<String, Object>> getKycStatus(@RequestParam("userId") Long userId);
}
