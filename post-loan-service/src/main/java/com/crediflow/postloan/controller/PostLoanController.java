package com.crediflow.postloan.controller;

import com.crediflow.common.web.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/post-loan")
public class PostLoanController {

    @PostMapping("/trigger-overdue-inspection")
    public Result<Void> triggerOverdueInspection() {
        log.info("Triggering daily overdue inspection...");
        // 实际上这里应该调用 repayment-service 查出所有昨天到期但 PENDING 的计划，
        // 并在 post-loan 记录逾期记录，然后通知 repayment-service 修改状态和增加罚息。
        // 为简化演示，这里 Mock 业务逻辑完成
        log.info("Overdue inspection completed.");
        return Result.success(null);
    }
}
