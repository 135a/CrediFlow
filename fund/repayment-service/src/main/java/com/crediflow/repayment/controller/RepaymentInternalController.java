package com.crediflow.repayment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.web.Result;
import com.crediflow.repayment.entity.RepaymentPlan;
import com.crediflow.repayment.service.RepaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 内部接口：供 Go {@code batch-service} 在定时代扣前批量拉取「今天应还且未受理」的期次。
 * 路径前缀 {@code /api/internal/**} 由 {@code InternalAuthFilter} 强制校验内网签名。
 */
@RestController
@RequestMapping("/api/internal/repayment")
public class RepaymentInternalController {

    @Autowired
    private RepaymentService repaymentService;

    /**
     * 查询今天到期且尚未进入资金方处理流程的期次。
     *
     * <p>过滤条件：</p>
     * <ul>
     *   <li>{@code due_date <= 今天 23:59:59}（含逾期）</li>
     *   <li>{@code status IN (PENDING, OVERDUE)}（排除 SUBMITTED / PAID / FAILED）</li>
     * </ul>
     *
     * <p>返回的 {@code businessOrderNo} 已带上随机后缀，可直接作为网关受理的业务单号。</p>
     */
    @GetMapping("/due-today")
    public Result<List<DueRepaymentView>> dueToday(@RequestParam(defaultValue = "500") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Date endOfToday = cal.getTime();

        List<RepaymentPlan> plans = repaymentService.list(new LambdaQueryWrapper<RepaymentPlan>()
                .in(RepaymentPlan::getStatus, "PENDING", "OVERDUE")
                .le(RepaymentPlan::getDueDate, endOfToday)
                .orderByAsc(RepaymentPlan::getDueDate)
                .last("LIMIT " + safeLimit));

        List<DueRepaymentView> data = new ArrayList<>(plans.size());
        for (RepaymentPlan plan : plans) {
            DueRepaymentView v = new DueRepaymentView();
            v.setPlanId(plan.getId());
            v.setUserId(plan.getUserId());
            v.setContractId(plan.getContractId());
            v.setApplicationId(plan.getApplicationId());
            v.setPeriod(plan.getPeriod());
            BigDecimal total = plan.getPrincipal().add(plan.getInterest());
            if (plan.getPenalty() != null) {
                total = total.add(plan.getPenalty());
            }
            v.setAmount(total.toPlainString());
            v.setCurrency("CNY");
            // 占位 bindCardId（token 化）；真实场景应来自用户服务的绑卡查询。
            v.setBindCardId("BC-USER-" + plan.getUserId());
            v.setBusinessOrderNo("WITHHOLD-PLAN-" + plan.getId() + "-" + UUID.randomUUID().toString().substring(0, 8));
            data.add(v);
        }
        return Result.success(data);
    }

    /** 内部 DTO；不参与 OpenAPI 暴露。 */
    public static class DueRepaymentView {
        private Long planId;
        private Long userId;
        private Long contractId;
        private Long applicationId;
        private Integer period;
        private String amount;
        private String currency;
        private String bindCardId;
        private String businessOrderNo;

        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getContractId() { return contractId; }
        public void setContractId(Long contractId) { this.contractId = contractId; }
        public Long getApplicationId() { return applicationId; }
        public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
        public Integer getPeriod() { return period; }
        public void setPeriod(Integer period) { this.period = period; }
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getBindCardId() { return bindCardId; }
        public void setBindCardId(String bindCardId) { this.bindCardId = bindCardId; }
        public String getBusinessOrderNo() { return businessOrderNo; }
        public void setBusinessOrderNo(String businessOrderNo) { this.businessOrderNo = businessOrderNo; }
    }
}
