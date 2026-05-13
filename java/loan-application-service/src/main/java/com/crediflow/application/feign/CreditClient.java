package com.crediflow.application.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "credit-risk-service", fallback = CreditClientFallback.class)
public interface CreditClient {

    @GetMapping("/api/app/credit/internal/active")
    Result<Map<String, Object>> getActiveCreditInternal(@RequestParam("userId") Long userId);
}

class CreditClientFallback implements CreditClient {
    @Override
    public Result<Map<String, Object>> getActiveCreditInternal(Long userId) {
        return Result.error(500, "授信服务调用失败，无法查询额度", null);
    }
}
