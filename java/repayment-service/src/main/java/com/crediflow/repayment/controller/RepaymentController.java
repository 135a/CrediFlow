package com.crediflow.repayment.controller;

import com.crediflow.common.web.Result;
import com.crediflow.repayment.entity.RepaymentPlan;
import com.crediflow.repayment.service.RepaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/repayment")
public class RepaymentController {

    @Autowired
    private RepaymentService repaymentService;

    /**
     * 用户主动还款。
     *
     * <p>请求 MUST 携带从前置接口（订单详情/提交确认页）获取的 {@code Idempotency-Key}（即 idmpToken），
     * 服务端基于 Redis 锁阻止重复点击，并将真正的资金外呼委托给 Go 资金网关（{@code /internal/v1/repay}）。
     * 终态由 {@code REPAYMENT_SETTLED_EVENT} 桥接事件异步推进。</p>
     */
    @PostMapping("/pay/{planId}")
    @com.crediflow.common.annotation.Idempotent(key = "'REPAY_APP:' + #idmpToken")
    public Result<RepaymentPlan> pay(@PathVariable Long planId,
                                     @RequestHeader("X-User-Id") Long userId,
                                     @RequestHeader("Idempotency-Key") String idmpToken) {
        return Result.success(repaymentService.activeRepay(userId, planId, idmpToken));
    }
}
