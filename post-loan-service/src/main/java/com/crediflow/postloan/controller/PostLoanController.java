package com.crediflow.postloan.controller;

import com.crediflow.common.auth.annotation.Inner;
import com.crediflow.common.web.Result;
import com.crediflow.postloan.service.PostLoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/internal/post-loan")
public class PostLoanController {

    @Autowired
    private PostLoanService postLoanService;

    // 此接口主要由调度任务（batch-service）调用
    @Inner
    @PostMapping("/overdue/process")
    public Result<Void> processOverdue(@RequestParam Long planId,
                                       @RequestParam Long contractId,
                                       @RequestParam Long userId,
                                       @RequestParam Integer overdueDays,
                                       @RequestParam BigDecimal principal) {
        postLoanService.processOverdue(planId, contractId, userId, overdueDays, principal);
        return Result.success(null);
    }
}
