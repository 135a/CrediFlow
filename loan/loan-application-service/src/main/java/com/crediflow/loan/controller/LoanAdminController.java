package com.crediflow.loan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crediflow.common.web.Result;
import com.crediflow.loan.entity.LoanApplication;
import com.crediflow.loan.service.LoanApplicationService;
import com.crediflow.loan.feign.UserClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/api/admin/loan")
public class LoanAdminController {

    @Autowired
    private LoanApplicationService loanApplicationService;

    @Autowired
    private UserClient userClient;

    @GetMapping("/applications")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>>> listApplications(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) String phone) {
        return Result.success(loanApplicationService.listAdminApplications(current, size, startTime, endTime, phone));
    }
}
