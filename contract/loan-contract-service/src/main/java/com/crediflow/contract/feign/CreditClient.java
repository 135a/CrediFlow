package com.crediflow.contract.feign;

import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign客户端接口，用于与信贷风险服务进行通信
 * 通过@FeignClient注解指定了要调用的服务名称为"credit-risk-service"
 */
@FeignClient(name = "credit-risk-service")
public interface CreditClient {

    /**
     * 扣减信额度的接口方法
     *
     * @param req 包含扣减额度所需参数的Map对象
     * @return 返回操作结果，Result<Void>表示不包含具体数据的操作结果
     */
    @PostMapping("/api/internal/credit/quota/deduct")
    Result<Void> deductQuota(@RequestBody Map<String, Object> req);
}
