package com.crediflow.credit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        
        LambdaQueryWrapper<CreditApplication> queryWrapper = new LambdaQueryWrapper<>();
        if (startTime != null) {
            queryWrapper.ge(CreditApplication::getCreatedAt, startTime);
        }
        if (endTime != null) {
            queryWrapper.le(CreditApplication::getCreatedAt, endTime);
        }
        queryWrapper.orderByDesc(CreditApplication::getCreatedAt);

        Page<CreditApplication> page = new Page<>(current, size);
        return Result.success(creditApplicationService.page(page, queryWrapper));
    }

    @Autowired
    private com.crediflow.credit.service.CreditService creditService;

    @PostMapping("/application/{id}/approve")
    public Result<Void> manualApprove(@PathVariable("id") Long id, @RequestParam(required = false, defaultValue = "") String remark) {
        creditService.manualApprove(id, remark);
        return Result.success(null);
    }
}
