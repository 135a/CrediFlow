package com.crediflow.bff.app.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "credit-risk-service")
public interface CreditClient {

    @PostMapping("/api/internal/credit/apply")
    Result<Map<String, Object>> applyCredit(@RequestParam("userId") Long userId);

    @GetMapping("/api/internal/credit/status")
    Result<Map<String, Object>> getCreditStatus(@RequestParam("userId") Long userId);

    @GetMapping("/api/internal/credit/quota")
    Result<Map<String, Object>> getCreditQuota(@RequestParam("userId") Long userId);

    @GetMapping("/api/internal/credit/last-result")
    Result<Map<String, Object>> getLastResult(@RequestParam("userId") Long userId);
}
