package com.crediflow.fund.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.fund.entity.FundFlow;

public interface FundFlowService extends IService<FundFlow> {
    void processDisbursement(Long applicationId, Long userId);
}
