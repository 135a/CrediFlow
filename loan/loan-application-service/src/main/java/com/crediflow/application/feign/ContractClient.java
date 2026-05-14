package com.crediflow.application.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "loan-contract-service")
public interface ContractClient {

    /**
     * 查询授信合同状态
     */
    @GetMapping("/api/internal/contract/credit-status")
    Result<Map<String, Object>> getCreditContractStatus(@RequestParam("userId") Long userId);
}
