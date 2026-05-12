package com.crediflow.fund.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.fund.entity.FundFlow;
import com.crediflow.fund.mapper.FundFlowMapper;
import com.crediflow.fund.service.FundFlowService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Service
public class FundFlowServiceImpl extends ServiceImpl<FundFlowMapper, FundFlow> implements FundFlowService {

    @Autowired
    private com.crediflow.fund.mapper.MqIdempotentLogMapper mqIdempotentLogMapper;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void processDisbursement(Long applicationId, Long userId) {
        String msgId = "DISBURSE:" + applicationId;
        
        com.crediflow.fund.entity.MqIdempotentLog log = new com.crediflow.fund.entity.MqIdempotentLog();
        log.setMessageId(msgId);
        log.setTopic(com.crediflow.common.event.MqConstants.TOPIC_LOAN_LIFECYCLE);
        log.setTag(com.crediflow.common.event.MqConstants.TAG_CONTRACT_READY);
        log.setConsumerGroup("fund-flow-group");
        log.setCreatedAt(new Date());

        try {
            mqIdempotentLogMapper.insert(log);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Already processed
            org.slf4j.LoggerFactory.getLogger(FundFlowServiceImpl.class).info("Application {} already disbursed, ignoring MQ event", applicationId);
            return;
        }

        // TODO: 从 loan-application 获取借款金额，这里暂时写死或者需要调用 loan-application 接口
        // 为简化，可以假设每次调用都能获取到金额，这里用 mock 金额 10000 替代
        
        FundFlow flow = new FundFlow();
        flow.setFlowNo("DIS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        flow.setApplicationId(applicationId);
        flow.setUserId(userId);
        flow.setAmount(new BigDecimal("10000.00")); // Mock amount
        flow.setType("DISBURSE");
        flow.setStatus("SUCCESS"); // Mock payment success
        flow.setThirdPartyTradeNo("ALIPAY" + System.currentTimeMillis());
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        
        this.save(flow);
    }
}
