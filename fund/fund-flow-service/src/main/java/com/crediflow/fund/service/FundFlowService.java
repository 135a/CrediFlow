package com.crediflow.fund.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.fund.entity.FundFlow;

public interface FundFlowService extends IService<FundFlow> {
    /**
     * 处理合同就绪后的放款受理。
     *
     * @return 是否仍由本服务立即投递 {@code FUND_DISBURSED} 生命周期消息（兼容开关）。
     *         当走资金网关且关闭兼容开关时返回 {@code false}，由网关 RocketMQ 桥接事件驱动终态。
     */
    boolean processDisbursement(com.crediflow.common.event.LoanLifecycleMessage message);
    void recordFlow(Long userId, String type, java.math.BigDecimal amount, String tradeNo, String status);
    boolean verifyThirdPartyCallback(java.util.Map<String, String> params);
}
