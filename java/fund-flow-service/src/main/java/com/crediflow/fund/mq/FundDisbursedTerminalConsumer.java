package com.crediflow.fund.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.event.FundDisbursedTerminalEvent;
import com.crediflow.common.event.LoanLifecycleMessage;
import com.crediflow.common.event.MqConstants;
import com.crediflow.fund.entity.FundFlow;
import com.crediflow.fund.entity.MqIdempotentLog;
import com.crediflow.fund.mapper.FundFlowMapper;
import com.crediflow.fund.mapper.MqIdempotentLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 消费 Go {@code fund-channel-gateway} 在收到资金方放款异步回调后桥接的终态事件。
 *
 * <p>独立 Topic（{@link MqConstants#TOPIC_FUND_DISBURSED_TERMINAL}），与
 * 旧版 {@code loan-lifecycle-topic + FUND_DISBURSED_EVENT}（{@link LoanLifecycleMessage} 载荷）
 * 区分开：</p>
 * <ul>
 *   <li>旧 Topic：合同就绪后立即广播给下游 (生成还款计划等)，由 {@code ContractReadyConsumer} 触发；</li>
 *   <li>新 Topic：真实资金方异步回调后由网关投递，仅 fund-flow-service / post-loan-service 关心。</li>
 * </ul>
 *
 * <p>幂等键采用 {@code gatewayRequestId}（业务侧维度），重复投递只更新一次终态。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_FUND_DISBURSED_TERMINAL, consumerGroup = "fund-flow-gateway-disbursed-group")
public class FundDisbursedTerminalConsumer implements RocketMQListener<FundDisbursedTerminalEvent> {

    private static final String CONSUMER_GROUP = "fund-flow-gateway-disbursed-group";

    @Autowired
    private MqIdempotentLogMapper mqIdempotentLogMapper;

    @Autowired
    private FundFlowMapper fundFlowMapper;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    @Transactional
    public void onMessage(FundDisbursedTerminalEvent event) {
        log.info("Received gateway-bridged FUND_DISBURSED terminal event biz={} txn={} terminal={}",
                event.getBusinessOrderNo(), event.getProviderTxnNo(), event.getTerminal());

        String idempotencyKey = buildIdempotencyKey(event);
        if (!claimIdempotent(idempotencyKey, MqConstants.TOPIC_FUND_DISBURSED_TERMINAL)) {
            log.info("Duplicate gateway terminal event ignored: {}", idempotencyKey);
            return;
        }

        FundFlow flow = lookupAcceptedFlow(event);
        if (flow == null) {
            log.warn("No ACCEPTED flow found for businessOrderNo={} gatewayReqId={}; recording orphan terminal",
                    event.getBusinessOrderNo(), event.getGatewayRequestId());
            recordOrphanFlow(event);
        } else {
            flow.setStatus(isSuccess(event) ? "SUCCESS" : "FAILED");
            flow.setProviderTxnNo(event.getProviderTxnNo());
            flow.setPayloadDigest(event.getPayloadDigest());
            flow.setUpdatedAt(new Date());
            fundFlowMapper.updateById(flow);
        }

        if (isSuccess(event)) {
            bridgeLegacyLifecycleEvent(event);
        } else {
            log.warn("Disbursement FAILED for biz={} code={} reason={}; downstream NOT advanced to repayment plan",
                    event.getBusinessOrderNo(), event.getFailureCode(), event.getFailureReason());
        }
    }

    private String buildIdempotencyKey(FundDisbursedTerminalEvent event) {
        if (event.getGatewayRequestId() != null && !event.getGatewayRequestId().isBlank()) {
            return "GW-DISB:" + event.getGatewayRequestId();
        }
        return "GW-DISB:" + event.getProviderId() + ":" + event.getProviderTxnNo();
    }

    private boolean claimIdempotent(String key, String topic) {
        MqIdempotentLog log = new MqIdempotentLog();
        log.setMessageId(key);
        log.setTopic(topic);
        log.setTag("GATEWAY_TERMINAL");
        log.setConsumerGroup(CONSUMER_GROUP);
        log.setCreatedAt(new Date());
        try {
            mqIdempotentLogMapper.insert(log);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private FundFlow lookupAcceptedFlow(FundDisbursedTerminalEvent event) {
        LambdaQueryWrapper<FundFlow> wrapper = new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getGatewayRequestId, event.getGatewayRequestId())
                .last("LIMIT 1");
        FundFlow flow = fundFlowMapper.selectOne(wrapper);
        if (flow != null) {
            return flow;
        }
        // Fallback: 在受理阶段 gatewayRequestId 可能延后落库，按 applicationId 检索
        if (event.getApplicationId() == null || event.getApplicationId().isBlank()) {
            return null;
        }
        try {
            Long applicationId = Long.parseLong(event.getApplicationId());
            return fundFlowMapper.selectOne(new LambdaQueryWrapper<FundFlow>()
                    .eq(FundFlow::getApplicationId, applicationId)
                    .eq(FundFlow::getType, "DISBURSE")
                    .orderByDesc(FundFlow::getId)
                    .last("LIMIT 1"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void recordOrphanFlow(FundDisbursedTerminalEvent event) {
        FundFlow flow = new FundFlow();
        flow.setFlowNo("ORPH-" + event.getGatewayRequestId());
        try {
            flow.setApplicationId(event.getApplicationId() == null ? null : Long.parseLong(event.getApplicationId()));
        } catch (NumberFormatException ignored) {
            flow.setApplicationId(null);
        }
        try {
            flow.setUserId(event.getUserId() == null ? null : Long.parseLong(event.getUserId()));
        } catch (NumberFormatException ignored) {
            flow.setUserId(null);
        }
        flow.setType("DISBURSE");
        flow.setStatus(isSuccess(event) ? "SUCCESS_ORPHAN" : "FAILED_ORPHAN");
        flow.setProviderId(event.getProviderId());
        flow.setGatewayRequestId(event.getGatewayRequestId());
        flow.setProviderTxnNo(event.getProviderTxnNo());
        flow.setPayloadDigest(event.getPayloadDigest());
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        fundFlowMapper.insert(flow);
    }

    private boolean isSuccess(FundDisbursedTerminalEvent event) {
        return "SUCCESS".equalsIgnoreCase(event.getTerminal());
    }

    /**
     * 将网关桥接的终态成功映射回旧的 {@code FUND_DISBURSED_EVENT}（LoanLifecycleMessage）流，
     * 让 repayment-service 等存量消费者无需立刻迁移即可触发还款计划生成。
     * 当所有下游消费者都直接订阅终态 Topic 后，这里可以下线。
     */
    private void bridgeLegacyLifecycleEvent(FundDisbursedTerminalEvent event) {
        if (event.getApplicationId() == null || event.getApplicationId().isBlank()
                || event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("Cannot bridge legacy FUND_DISBURSED_EVENT: missing applicationId/userId. biz={}", event.getBusinessOrderNo());
            return;
        }
        try {
            LoanLifecycleMessage legacy = new LoanLifecycleMessage();
            legacy.setLoanApplicationId(Long.parseLong(event.getApplicationId()));
            legacy.setUserId(Long.parseLong(event.getUserId()));
            legacy.setEventType(MqConstants.TAG_FUND_DISBURSED);
            rocketMQTemplate.convertAndSend(
                    MqConstants.TOPIC_LOAN_LIFECYCLE + ":" + MqConstants.TAG_FUND_DISBURSED, legacy);
            log.info("Bridged gateway terminal -> legacy FUND_DISBURSED_EVENT for application={}", legacy.getLoanApplicationId());
        } catch (NumberFormatException e) {
            log.error("Bridge legacy event failed parsing ids biz={}", event.getBusinessOrderNo(), e);
        }
    }
}
