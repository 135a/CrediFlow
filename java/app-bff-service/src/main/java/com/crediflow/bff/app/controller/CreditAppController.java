package com.crediflow.bff.app.controller;

import com.crediflow.bff.app.feign.CreditClient;
import com.crediflow.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/app/credit")
public class CreditAppController {

    @Autowired
    private CreditClient creditClient;

    @PostMapping("/apply")
    public Result<Map<String, Object>> applyCredit(@RequestHeader("X-User-Id") Long userId) {
        try {
            return creditClient.applyCredit(userId);
        } catch (Exception e) {
            // 4.2 错误码映射：不透出内部规则码
            return Result.error(500, "授信申请处理异常，请稍后再试");
        }
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getCreditStatus(@RequestHeader("X-User-Id") Long userId) {
        return creditClient.getCreditStatus(userId);
    }

    @GetMapping("/quota")
    public Result<Map<String, Object>> getCreditQuota(@RequestHeader("X-User-Id") Long userId) {
        return creditClient.getCreditQuota(userId);
    }

    @GetMapping("/last-result")
    public Result<Map<String, Object>> getLastResult(@RequestHeader("X-User-Id") Long userId) {
        try {
            Result<Map<String, Object>> result = creditClient.getLastResult(userId);
            // 这里也可以做一些脱敏处理，确保 userSafeInsight 的输出
            return result;
        } catch (Exception e) {
            return Result.error(500, "获取审批结果失败");
        }
    }
}
