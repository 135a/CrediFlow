package com.crediflow.bff.app.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign客户端接口，用于与信用风险评估服务进行通信
 * 通过@FeignClient注解指定服务名称为"credit-risk-service"
 */
@FeignClient(name = "credit-risk-service")
public interface CreditClient {

    /**
     * 申请信用的接口
     * @param userId 用户ID，作为请求参数
     * @return 返回一个包含Map类型数据的Result对象
     */
    @PostMapping("/api/internal/credit/apply")
    Result<Map<String, Object>> applyCredit(@RequestParam("userId") Long userId);

    /**
     * 获取信用状态的接口
     * @param userId 用户ID，作为请求参数
     * @return 返回一个包含Map类型数据的Result对象
     */
    @GetMapping("/api/internal/credit/status")
    Result<Map<String, Object>> getCreditStatus(@RequestParam("userId") Long userId);

    /**
     * 获取信用额度的接口
     * @param userId 用户ID，作为请求参数
     * @return 返回一个包含Map类型数据的Result对象
     */
    @GetMapping("/api/internal/credit/quota")
    Result<Map<String, Object>> getCreditQuota(@RequestParam("userId") Long userId);

    /**
     * 获取最后一次信用评估结果的接口
     * @param userId 用户ID，作为请求参数
     * @return 返回一个包含Map类型数据的Result对象
     */
    @GetMapping("/api/internal/credit/last-result")
    Result<Map<String, Object>> getLastResult(@RequestParam("userId") Long userId);
}
