package com.crediflow.user.controller;

import com.crediflow.common.web.Result;
import com.crediflow.user.dto.BatchPushRequest;
import com.crediflow.user.dto.RepaymentReminderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/user/notify")
public class UserNotifyInternalController {

    @PostMapping("/repayment-reminder")
    public Result<Void> repaymentReminder(@RequestBody RepaymentReminderRequest request) {
        log.info("Received repayment reminder for due date: {}, type: {}, source: {}", 
                 request.getDueDate(), request.getReminderType(), request.getTriggerSource());
        // Mock 业务逻辑完成
        log.info("Repayment reminder notification completed.");
        return Result.success(null);
    }

    @PostMapping("/batch-push")
    public Result<Void> batchPush(@RequestBody BatchPushRequest request) {
        log.info("Received batch push for time: {}, types: {}, source: {}", 
                 request.getBatchTime(), request.getTypes(), request.getTriggerSource());
        // Mock 业务逻辑完成
        log.info("Batch push notification completed.");
        return Result.success(null);
    }
}
