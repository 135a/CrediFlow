package com.crediflow.postloan.mq;

import com.crediflow.common.event.FundDisbursedTerminalEvent;
import com.crediflow.common.event.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 贷后画像消费 Go 资金网关桥接的放款终态事件。
 *
 * <p>关键关注点（任务 10.2）：放款失败 MUST NOT 错误地把借据推进到「还款中」。
 * 本消费者仅在收到 SUCCESS 时考虑触发履约视图初始化；失败终态仅做审计记录。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_FUND_DISBURSED_TERMINAL, consumerGroup = "post-loan-gateway-disbursed-group")
public class FundDisbursedTerminalConsumer implements RocketMQListener<FundDisbursedTerminalEvent> {

    private static final String IDEMPOTENCY_PREFIX = "GW-DISB-PROFILE:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(FundDisbursedTerminalEvent event) {
        log.info("PostLoan received gateway FUND_DISBURSED terminal biz={} gwReqId={} terminal={}",
                event.getBusinessOrderNo(), event.getGatewayRequestId(), event.getTerminal());

        String key = IDEMPOTENCY_PREFIX + event.getGatewayRequestId();
        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(firstTime)) {
            log.info("Duplicate post-loan disbursement terminal ignored: {}", key);
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(event.getTerminal())) {
            log.info("POST_LOAN_AUDIT disbursed userId={} application={} amount={} providerTxn={}",
                    event.getUserId(), event.getApplicationId(), event.getAmount(), event.getProviderTxnNo());
            // 占位：批次 4 接入「初始化用户履约视图 / 通知画像服务」。
            return;
        }

        // 失败终态：审计记录 + 显式强调 NOT 进入「还款中」。
        log.warn("POST_LOAN_DISBURSE_FAILED biz={} application={} code={} reason={}; downstream MUST NOT enter REPAYING state",
                event.getBusinessOrderNo(), event.getApplicationId(),
                event.getFailureCode(), event.getFailureReason());
    }
}
