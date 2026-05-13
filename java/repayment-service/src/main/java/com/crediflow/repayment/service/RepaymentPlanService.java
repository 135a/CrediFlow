package com.crediflow.repayment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.repayment.entity.RepaymentPlan;

public interface RepaymentPlanService extends IService<RepaymentPlan> {
    void generateRepaymentPlan(Long applicationId, Long userId);

    /**
     * @deprecated 旧的 Mock 收银网关直连路径，已被 {@link RepaymentService#activeRepay(Long, Long, String)}
     *             经 Go 资金网关受理 + RocketMQ 终态桥接的链路取代；调用此方法将抛 {@link UnsupportedOperationException}。
     */
    @Deprecated
    void processRepayment(Long planId, Long userId);
}
