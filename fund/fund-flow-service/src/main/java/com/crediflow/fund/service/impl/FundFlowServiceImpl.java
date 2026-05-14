package com.crediflow.fund.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.event.LoanLifecycleMessage;
import com.crediflow.common.event.MqConstants;
import com.crediflow.fund.client.FundChannelGatewayClient;
import com.crediflow.fund.client.dto.FundChannelDisburseRequest;
import com.crediflow.fund.client.dto.FundChannelDisburseResponse;
import com.crediflow.fund.entity.FundFlow;
import com.crediflow.fund.entity.MqIdempotentLog;
import com.crediflow.fund.mapper.FundFlowMapper;
import com.crediflow.fund.mapper.MqIdempotentLogMapper;
import com.crediflow.fund.service.FundFlowService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FundFlowServiceImpl extends ServiceImpl<FundFlowMapper, FundFlow> implements FundFlowService {

    @Autowired
    private MqIdempotentLogMapper mqIdempotentLogMapper;

    @Autowired
    private FundChannelGatewayClient fundChannelGatewayClient;

    @Value("${crediflow.fund.use-gateway:false}")
    private boolean useFundGateway;

    /**
     * 在启用资金网关且网关返回 ACCEPTED 时，是否仍由本服务立即发送旧的 {@code FUND_DISBURSED}
     * 生命周期消息。默认 true，便于在尚未接入网关 MQ 消费者（任务 6.3）前保持还款计划生成链路不断。
     */
    @Value("${crediflow.fund.emit-legacy-fund-disbursed-after-gateway:true}")
    private boolean emitLegacyFundDisbursedAfterGateway;

    @Override
    @Transactional
    public boolean processDisbursement(LoanLifecycleMessage message) {
        Long applicationId = message.getLoanApplicationId();
        Long userId = message.getUserId();
        String msgId = "DISBURSE:" + applicationId;

        MqIdempotentLog idempotent = new MqIdempotentLog();
        idempotent.setMessageId(msgId);
        idempotent.setTopic(MqConstants.TOPIC_LOAN_LIFECYCLE);
        idempotent.setTag(MqConstants.TAG_CONTRACT_READY);
        idempotent.setConsumerGroup("fund-flow-group");
        idempotent.setCreatedAt(new Date());

        try {
            mqIdempotentLogMapper.insert(idempotent);
        } catch (DuplicateKeyException e) {
            log.info("Application {} already disbursed (idempotent), ignoring MQ event", applicationId);
            return false;
        }

        if (!useFundGateway) {
            persistLegacyMockDisburse(applicationId, userId);
            return true;
        }

        FundChannelDisburseRequest req = buildDisburseRequest(message);
        FundChannelDisburseResponse resp;
        try {
            resp = fundChannelGatewayClient.disburse(req);
        } catch (FeignException ex) {
            log.error("Fund gateway disburse call failed status={} body={}", ex.status(), ex.contentUTF8(), ex);
            throw new IllegalStateException("fund-channel-gateway disburse failed", ex);
        }

        if (!"ACCEPTED".equalsIgnoreCase(resp.getState())) {
            log.warn("Fund gateway non-accepted state={} code={} msg={}", resp.getState(), resp.getErrorCode(), resp.getErrorMessage());
            throw new IllegalStateException("fund gateway state " + resp.getState());
        }

        FundFlow flow = new FundFlow();
        flow.setFlowNo("DIS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        flow.setApplicationId(applicationId);
        flow.setUserId(userId);
        flow.setAmount(parseAmount(message));
        flow.setType("DISBURSE");
        flow.setStatus("ACCEPTED");
        flow.setThirdPartyTradeNo(null);
        flow.setProviderId(resp.getProviderId());
        flow.setGatewayRequestId(resp.getGatewayRequestId());
        flow.setProviderTxnNo(null);
        flow.setPayloadDigest(null);
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        this.save(flow);

        return emitLegacyFundDisbursedAfterGateway;
    }

    private void persistLegacyMockDisburse(Long applicationId, Long userId) {
        FundFlow flow = new FundFlow();
        flow.setFlowNo("DIS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        flow.setApplicationId(applicationId);
        flow.setUserId(userId);
        flow.setAmount(new BigDecimal("10000.00"));
        flow.setType("DISBURSE");
        flow.setStatus("SUCCESS");
        flow.setThirdPartyTradeNo("MOCK-LEGACY-" + System.currentTimeMillis());
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        this.save(flow);
    }

    private FundChannelDisburseRequest buildDisburseRequest(LoanLifecycleMessage message) {
        Long applicationId = message.getLoanApplicationId();
        Long userId = message.getUserId();
        Map<String, String> extra = new HashMap<>();
        extra.put("applicationId", String.valueOf(applicationId));

        FundChannelDisburseRequest req = new FundChannelDisburseRequest();
        req.setBusinessOrderNo("APP-" + applicationId);
        req.setUserId(String.valueOf(userId));
        req.setBindCardId(readPayloadString(message, "bindCardId", "BC-APP-" + applicationId));
        req.setAmount(readPayloadString(message, "amount", "10000.00"));
        req.setCurrency(readPayloadString(message, "currency", "CNY"));
        req.setInstallments(readPayloadInt(message, "installments", 12));
        req.setTriggerSource("disburse-chain");
        String providerId = readPayloadString(message, "providerId", null);
        if (providerId != null && !providerId.isBlank()) {
            req.setProviderId(providerId);
        }
        req.setExtra(extra);
        return req;
    }

    private BigDecimal parseAmount(LoanLifecycleMessage message) {
        return new BigDecimal(readPayloadString(message, "amount", "10000.00"));
    }

    private static String readPayloadString(LoanLifecycleMessage message, String key, String defaultVal) {
        if (!(message.getPayload() instanceof Map<?, ?> map)) {
            return defaultVal;
        }
        Object v = map.get(key);
        if (v == null) {
            return defaultVal;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? defaultVal : s;
    }

    private static int readPayloadInt(LoanLifecycleMessage message, String key, int defaultVal) {
        if (!(message.getPayload() instanceof Map<?, ?> map)) {
            return defaultVal;
        }
        Object v = map.get(key);
        if (v == null) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    @Override
    public void recordFlow(Long userId, String type, BigDecimal amount, String tradeNo, String status) {
        FundFlow flow = new FundFlow();
        flow.setFlowNo("FLW" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        flow.setUserId(userId);
        flow.setAmount(amount);
        flow.setType(type);
        flow.setThirdPartyTradeNo(tradeNo);
        flow.setStatus(status);
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        this.save(flow);
    }

    @Override
    public boolean verifyThirdPartyCallback(Map<String, String> params) {
        if (useFundGateway) {
            log.warn("Legacy payment callback rejected while crediflow.fund.use-gateway=true, paramsKeys={}", params.keySet());
            return false;
        }
        return true;
    }
}
