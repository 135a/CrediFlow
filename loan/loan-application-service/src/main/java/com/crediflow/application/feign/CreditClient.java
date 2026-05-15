package com.crediflow.application.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "credit-risk-service", fallback = CreditClientFallback.class)
public interface CreditClient {

    @GetMapping("/api/internal/credit/active")
    Result<Map<String, Object>> getActiveCreditInternal(@RequestParam("userId") Long userId);

    @org.springframework.web.bind.annotation.PostMapping("/api/internal/credit/evaluate-loan")
    Result<String> evaluateLoanRisk(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> req);

    @org.springframework.web.bind.annotation.PostMapping("/api/internal/credit/review/enqueue")
    Result<Void> enqueueLoanReview(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> req);
}

class CreditClientFallback implements CreditClient {
    @Override
    public Result<Map<String, Object>> getActiveCreditInternal(Long userId) {
        return Result.error(500, "授信服务调用失败，无法查询额度", null);
    }

    @Override
    public Result<String> evaluateLoanRisk(Map<String, Object> req) {
        return Result.error(500, "风控服务调用失败，无法进行借款评估", null);
    }

    @Override
    public Result<Void> enqueueLoanReview(Map<String, Object> req) {
        return Result.error(500, "风控服务调用失败，无法进入人工审核队列", null);
    }
}
