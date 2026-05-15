package com.crediflow.credit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.service.CreditApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/api/admin/credit")
public class CreditAdminController {

    @Autowired
    private CreditApplicationService creditApplicationService;

    @GetMapping("/applications")
    public Result<Page<CreditApplication>> listApplications(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) String phone) {
        
        return Result.success(creditApplicationService.listApplications(current, size, startTime, endTime, phone));
    }

    @Autowired
    private com.crediflow.credit.service.CreditService creditService;

    @PostMapping("/application/{id}/approve")
    public Result<Void> manualApprove(@PathVariable("id") Long id, @RequestParam(required = false, defaultValue = "") String remark) {
        creditService.manualApprove(id, remark);
        return Result.success(null);
    }
    
    @GetMapping("/manual-review-queue")
    public Result<Page<com.crediflow.credit.entity.CreditReviewQueue>> listReviewQueue(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(creditService.listReviewQueue(current, size));
    }
}
