package com.crediflow.repayment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.repayment.client.FundChannelGatewayClient;
import com.crediflow.repayment.client.dto.FundChannelRepayRequest;
import com.crediflow.repayment.client.dto.FundChannelRepayResponse;
import com.crediflow.repayment.entity.RepaymentPlan;
import com.crediflow.repayment.mapper.RepaymentPlanMapper;
import com.crediflow.repayment.service.RepaymentService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RepaymentServiceImpl extends ServiceImpl<RepaymentPlanMapper, RepaymentPlan> implements RepaymentService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private FundChannelGatewayClient fundChannelGatewayClient;

    /**
     * 主动还款是否经 Go 资金网关受理。默认 true：与「禁止 Java 直连资金方」的设计一致。
     * 仅作为应急回滚开关，生产关闭后必须人工评估资金安全。
     */
    @Value("${crediflow.repayment.use-gateway:true}")
    private boolean useFundGateway;

    @Value("${crediflow.repayment.default-provider-id:}")
    private String defaultProviderId;

    @Override
    public List<RepaymentPlan> generatePlans(Long userId, Long contractId, BigDecimal loanAmount, BigDecimal interestRate, Integer term) {
        BigDecimal principalPerPeriod = loanAmount.divide(new BigDecimal(term), 2, RoundingMode.HALF_UP);

        List<RepaymentPlan> plans = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        Date lastDate = cal.getTime();

        for (int i = 1; i <= term; i++) {
            cal.add(Calendar.MONTH, 1);
            Date dueDate = cal.getTime();

            long diffInMillies = Math.abs(dueDate.getTime() - lastDate.getTime());
            long daysInPeriod = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
            lastDate = dueDate;

            BigDecimal interestPerPeriod = loanAmount.multiply(interestRate).multiply(new BigDecimal(daysInPeriod)).setScale(2, RoundingMode.HALF_UP);

            RepaymentPlan plan = new RepaymentPlan();
            plan.setContractId(contractId);
            plan.setUserId(userId);
            plan.setPeriod(i);
            plan.setPrincipal(principalPerPeriod);
            plan.setInterest(interestPerPeriod);
            plan.setPenalty(BigDecimal.ZERO);
            plan.setStatus("PENDING");
            plan.setDueDate(dueDate);
            plan.setCreatedAt(new Date());
            plan.setUpdatedAt(new Date());
            plans.add(plan);
        }

        this.saveBatch(plans);
        return plans;
    }

    /**
     * 主动还款（用户点击「确认还款」）。流程：
     * <ol>
     *   <li>取得 Redis 幂等锁，阻止网络抖动产生的重复点击；</li>
     *   <li>校验计划状态：只接受 PENDING / OVERDUE，禁止对 SUBMITTED / PAID 重复发起；</li>
     *   <li>调用 Go 资金网关 {@code /internal/v1/repay} 受理（混合路由：业务可传 providerId）；</li>
     *   <li>将期次状态置 SUBMITTED，等待 {@code REPAYMENT_SETTLED_EVENT} 异步桥接终态。</li>
     * </ol>
     * 真正的核销动作在 {@link com.crediflow.repayment.mq.RepaymentSettledConsumer} 处理。
     */
    @Override
    public RepaymentPlan activeRepay(Long userId, Long planId, String idmpToken) {
        String key = "IDMP:REPAY:" + idmpToken;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请勿重复提交还款");
        }

        RepaymentPlan plan = this.getById(planId);
        try {
            if (plan == null || !plan.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "还款计划不存在");
            }
            if ("PAID".equals(plan.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该期已还清");
            }
            if ("SUBMITTED".equals(plan.getStatus())) {
                // 业务上视为「资金方处理中」，让前端轮询查询而非重新发起。中间态查询策略见
                // docs 中的 gateway-integration.md。
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "还款受理中，请稍后再试");
            }
            if (!"PENDING".equals(plan.getStatus()) && !"OVERDUE".equals(plan.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "还款计划状态非法，无法还款");
            }

            if (!useFundGateway) {
                log.warn("Repayment gateway disabled, falling back to legacy mock path planId={} userId={}", planId, userId);
                plan.setStatus("PAID");
                plan.setPaidTime(new Date());
                this.updateById(plan);
                return plan;
            }

            FundChannelRepayResponse resp = submitToGateway(plan, idmpToken);
            if (!"ACCEPTED".equalsIgnoreCase(resp.getState())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "资金网关拒绝受理：" + resp.getErrorCode() + " " + resp.getErrorMessage());
            }

            plan.setStatus("SUBMITTED");
            plan.setProviderId(resp.getProviderId());
            plan.setGatewayRequestId(resp.getGatewayRequestId());
            plan.setSubmittedAt(new Date());
            plan.setUpdatedAt(new Date());
            this.updateById(plan);
            log.info("Repayment submitted to fund-channel-gateway planId={} gatewayReqId={} provider={}",
                    planId, resp.getGatewayRequestId(), resp.getProviderId());
            return plan;
        } catch (BusinessException be) {
            redisTemplate.delete(key);
            throw be;
        } catch (FeignException fe) {
            redisTemplate.delete(key);
            log.error("Fund gateway repay call failed status={} body={}", fe.status(), fe.contentUTF8(), fe);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "资金网关暂不可用，请稍后再试");
        } catch (RuntimeException ex) {
            redisTemplate.delete(key);
            throw ex;
        }
    }

    private FundChannelRepayResponse submitToGateway(RepaymentPlan plan, String idmpToken) {
        BigDecimal due = plan.getPrincipal().add(plan.getInterest());
        if (plan.getPenalty() != null) {
            due = due.add(plan.getPenalty());
        }

        Map<String, String> extra = new HashMap<>();
        extra.put("planId", String.valueOf(plan.getId()));
        extra.put("idmpToken", idmpToken);
        if (plan.getContractId() != null) {
            extra.put("contractId", String.valueOf(plan.getContractId()));
        }
        if (plan.getApplicationId() != null) {
            extra.put("applicationId", String.valueOf(plan.getApplicationId()));
        }

        FundChannelRepayRequest req = new FundChannelRepayRequest();
        req.setBusinessOrderNo("REPAY-PLAN-" + plan.getId() + "-" + UUID.randomUUID().toString().substring(0, 8));
        req.setUserId(String.valueOf(plan.getUserId()));
        // bindCardId：业务库后续补全；阶段 3 先用 token 化占位（绝不传卡号明文）。
        req.setBindCardId("BC-USER-" + plan.getUserId());
        req.setAmount(due.toPlainString());
        req.setCurrency("CNY");
        req.setInstallments(plan.getPeriod());
        req.setTriggerSource("active");
        if (defaultProviderId != null && !defaultProviderId.isBlank()) {
            req.setProviderId(defaultProviderId);
        }
        req.setExtra(extra);

        return fundChannelGatewayClient.repay(req);
    }
}
