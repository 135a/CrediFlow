package com.crediflow.bff.admin.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "loan-application-service", fallback = LoanApplicationAdminClientFallback.class)
public interface LoanApplicationAdminClient {

    @GetMapping("/api/app/loan-application/internal/admin/list")
    Result<Map<String, Object>> listApplications(
            @RequestParam("page") Integer page,
            @RequestParam("size") Integer size,
            @RequestParam("status") String status);

    @GetMapping("/api/app/loan-application/internal/admin/{id}")
    Result<Map<String, Object>> getApplication(@PathVariable("id") Long id);

    @PostMapping("/api/app/loan-application/internal/admin/{id}/review")
    Result<Void> manualReview(@PathVariable("id") Long id,
                              @RequestParam("action") String action,
                              @RequestParam("reason") String reason,
                              @RequestParam("reviewerId") Long reviewerId);
}

class LoanApplicationAdminClientFallback implements LoanApplicationAdminClient {
    @Override
    public Result<Map<String, Object>> listApplications(Integer page, Integer size, String status) {
        return Result.error(500, "借款服务调用失败", null);
    }

    @Override
    public Result<Map<String, Object>> getApplication(Long id) {
        return Result.error(500, "借款服务调用失败", null);
    }

    @Override
    public Result<Void> manualReview(Long id, String action, String reason, Long reviewerId) {
        return Result.error(500, "借款服务调用失败", null);
    }
}
