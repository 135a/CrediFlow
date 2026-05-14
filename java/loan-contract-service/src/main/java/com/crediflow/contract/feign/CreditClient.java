package com.crediflow.contract.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "credit-risk-service")
public interface CreditClient {

    @PostMapping("/api/app/credit/internal/quota/deduct")
    Result<Void> deductQuota(@RequestBody Map<String, Object> req);
}
