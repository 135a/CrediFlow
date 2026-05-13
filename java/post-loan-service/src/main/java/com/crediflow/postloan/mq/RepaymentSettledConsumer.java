package com.crediflow.postloan.mq;

import com.crediflow.common.event.MqConstants;
import com.crediflow.common.event.RepaymentSettledEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 贷后画像消费 Go 资金网关桥接的还款结清终态事件。
 *
 * <p>幂等键：{@code GW-REPAY-PROFILE:<gatewayRequestId>}，Redis SETNX 24h。
 * 处理范围按本批次约束保持轻量：</p>
 * <ul>
 *   <li>成功终态 -> 写入履约日志（log + audit）并占位调用用户画像更新通道；</li>
 *   <li>失败终态 -> 仅记录原因，避免错误推进「还款中」状态。</li>
 * </ul>
 *
 * <p>真实的画像/标签更新接口对接放在批次 4，本类预留 {@code dispatchProfileUpdate}
 * 钩子，便于后续接入而不破坏 MQ 链路。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_REPAYMENT_SETTLED, consumerGroup = "post-loan-gateway-settled-group")
public class RepaymentSettledConsumer implements RocketMQListener<RepaymentSettledEvent> {

    private static final String IDEMPOTENCY_PREFIX = "GW-REPAY-PROFILE:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(RepaymentSettledEvent event) {
        log.info("PostLoan received gateway REPAYMENT_SETTLED biz={} gwReqId={} terminal={}",
                event.getBusinessOrderNo(), event.getGatewayRequestId(), event.getTerminal());

        String key = IDEMPOTENCY_PREFIX + event.getGatewayRequestId();
        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(firstTime)) {
            log.info("Duplicate post-loan repayment terminal ignored: {}", key);
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(event.getTerminal())) {
            recordSuccess(event);
            dispatchProfileUpdate(event);
        } else {
            log.warn("Post-loan observed repayment FAILED biz={} provider={} code={} reason={}",
                    event.getBusinessOrderNo(), event.getProviderId(),
                    event.getFailureCode(), event.getFailureReason());
        }
    }

    private void recordSuccess(RepaymentSettledEvent event) {
        // 审计入口：写日志便于现阶段对单；批次 4 接入用户画像/risk 服务后再升级为持久审计。
        log.info("POST_LOAN_AUDIT settled userId={} loanNo={} period={} amount={} providerTxn={} trigger={}",
                event.getUserId(), event.getLoanNo(), event.getInstallment(),
                event.getAmount(), event.getProviderTxnNo(), event.getTriggerSource());
    }

    /**
     * 履约标签更新 outbound 通道占位。批次 4 接入 user-profile / risk 服务后替换为
     * Feign 调用；当前阶段保留为可观测的占位日志，便于联调验证消息链路。
     */
    private void dispatchProfileUpdate(RepaymentSettledEvent event) {
        log.info("TODO[profile-outbound] dispatch on-time-repayment tag userId={} loanNo={} trigger={}",
                event.getUserId(), event.getLoanNo(), event.getTriggerSource());
    }
}
