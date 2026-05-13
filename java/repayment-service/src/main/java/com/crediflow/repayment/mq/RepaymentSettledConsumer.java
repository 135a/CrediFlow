package com.crediflow.repayment.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.event.MqConstants;
import com.crediflow.common.event.RepaymentSettledEvent;
import com.crediflow.repayment.entity.RepaymentPlan;
import com.crediflow.repayment.service.RepaymentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 消费 Go {@code fund-channel-gateway} 在收到资金方还款/代扣异步回调后桥接的终态事件。
 *
 * <p>幂等键采用 {@code GW-REPAY:<gatewayRequestId>}，落地 Redis SETNX 24h，
 * 重复投递 MUST NOT 重复核销期次或重复释放幂等锁。</p>
 *
 * <p>成功：将对应 {@link RepaymentPlan} 从 SUBMITTED 推进到 PAID 并回填资金方流水号；
 * 失败：状态回退至 PENDING，下次主动还款或代扣可重新发起（同时记录失败原因到日志）。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MqConstants.TOPIC_REPAYMENT_SETTLED, consumerGroup = "repayment-gateway-settled-group")
public class RepaymentSettledConsumer implements RocketMQListener<RepaymentSettledEvent> {

    private static final String IDEMPOTENCY_PREFIX = "GW-REPAY:";

    @Autowired
    private RepaymentService repaymentService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(RepaymentSettledEvent event) {
        log.info("Received gateway REPAYMENT_SETTLED biz={} gwReqId={} terminal={} trigger={}",
                event.getBusinessOrderNo(), event.getGatewayRequestId(), event.getTerminal(), event.getTriggerSource());

        String idmpKey = IDEMPOTENCY_PREFIX + event.getGatewayRequestId();
        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(idmpKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(firstTime)) {
            log.info("Duplicate gateway repayment terminal ignored: {}", idmpKey);
            return;
        }

        RepaymentPlan plan = lookupPlan(event);
        if (plan == null) {
            log.warn("No repayment plan matched gateway event biz={} gwReqId={}; ignoring",
                    event.getBusinessOrderNo(), event.getGatewayRequestId());
            return;
        }

        if (isSuccess(event)) {
            settle(plan, event);
        } else {
            rollback(plan, event);
        }
    }

    private boolean isSuccess(RepaymentSettledEvent event) {
        return "SUCCESS".equalsIgnoreCase(event.getTerminal());
    }

    private RepaymentPlan lookupPlan(RepaymentSettledEvent event) {
        // 受理阶段 RepaymentServiceImpl 已把 gatewayRequestId 写入计划；优先按它定位。
        if (event.getGatewayRequestId() != null && !event.getGatewayRequestId().isBlank()) {
            RepaymentPlan plan = repaymentService.getOne(new LambdaQueryWrapper<RepaymentPlan>()
                    .eq(RepaymentPlan::getGatewayRequestId, event.getGatewayRequestId())
                    .last("LIMIT 1"));
            if (plan != null) {
                return plan;
            }
        }
        // 回落：按 extra.planId 解析。businessOrderNo 形如 REPAY-PLAN-<id>-<uuid>，从中切片。
        Long planId = extractPlanId(event.getBusinessOrderNo());
        if (planId != null) {
            return repaymentService.getById(planId);
        }
        return null;
    }

    private Long extractPlanId(String businessOrderNo) {
        if (businessOrderNo == null || !businessOrderNo.startsWith("REPAY-PLAN-")) {
            return null;
        }
        String[] parts = businessOrderNo.split("-");
        if (parts.length < 3) {
            return null;
        }
        try {
            return Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void settle(RepaymentPlan plan, RepaymentSettledEvent event) {
        if ("PAID".equalsIgnoreCase(plan.getStatus())) {
            log.info("Plan {} already PAID; gateway terminal re-confirms (no-op)", plan.getId());
            return;
        }
        plan.setStatus("PAID");
        plan.setPaidTime(new Date());
        plan.setProviderTxnNo(event.getProviderTxnNo());
        if (event.getAmount() != null) {
            // 仅日志校验，避免覆盖业务侧已合计的金额。
            try {
                BigDecimal recv = new BigDecimal(event.getAmount());
                BigDecimal expected = plan.getPrincipal().add(plan.getInterest());
                if (plan.getPenalty() != null) {
                    expected = expected.add(plan.getPenalty());
                }
                if (recv.compareTo(expected) != 0) {
                    log.warn("Repayment terminal amount mismatch planId={} expected={} received={}",
                            plan.getId(), expected.toPlainString(), recv.toPlainString());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        plan.setUpdatedAt(new Date());
        repaymentService.updateById(plan);
        log.info("Plan {} settled via gateway. providerTxn={} trigger={}",
                plan.getId(), event.getProviderTxnNo(), event.getTriggerSource());
    }

    private void rollback(RepaymentPlan plan, RepaymentSettledEvent event) {
        if (!"SUBMITTED".equalsIgnoreCase(plan.getStatus())) {
            log.warn("Gateway reported FAILED for planId={} but local status={}; keeping local state",
                    plan.getId(), plan.getStatus());
            return;
        }
        plan.setStatus("PENDING");
        plan.setUpdatedAt(new Date());
        repaymentService.updateById(plan);
        log.warn("Plan {} repayment FAILED at provider. code={} reason={}; status rolled back to PENDING",
                plan.getId(), event.getFailureCode(), event.getFailureReason());
    }
}
