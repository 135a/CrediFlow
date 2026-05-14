package com.crediflow.bff.app.controller;

import com.crediflow.bff.app.feign.CreditClient;
import com.crediflow.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 授信应用控制器
 * 提供授信申请、状态查询、额度查询和最新结果查询等功能
 */
@RestController
@RequestMapping("/api/app/credit")
public class CreditAppController {

    /**
     * 注入授信客户端服务
     */
    @Autowired
    private CreditClient creditClient;

    /**
     * 授信申请接口
     * @param userId 用户ID，从请求头X-User-Id中获取
     * @return 返回授信申请结果
     */
    @PostMapping("/apply")
    public Result<Map<String, Object>> applyCredit(@RequestHeader("X-User-Id") Long userId) {
        try {
            return creditClient.applyCredit(userId);
        } catch (Exception e) {
            // 4.2 错误码映射：不透出内部规则码
            return Result.error(500, "授信申请处理异常，请稍后再试");
        }
    }

    /**
     * 获取授信状态接口
     * @param userId 用户ID，从请求头X-User-Id中获取
     * @return 返回授信状态信息
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getCreditStatus(@RequestHeader("X-User-Id") Long userId) {
        return creditClient.getCreditStatus(userId);
    }

    /**
     * 获取授信额度接口
     * @param userId 用户ID，从请求头X-User-Id中获取
     * @return 返回授信额度信息
     */
    @GetMapping("/quota")
    public Result<Map<String, Object>> getCreditQuota(@RequestHeader("X-User-Id") Long userId) {
        return creditClient.getCreditQuota(userId);
    }

    /**
     * 获取最新审批结果接口
     * @param userId 用户ID，从请求头X-User-Id中获取
     * @return 返回最新审批结果
     */
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
