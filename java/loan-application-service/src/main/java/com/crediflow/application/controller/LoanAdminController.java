package com.crediflow.application.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crediflow.common.web.Result;
import com.crediflow.application.entity.LoanApplication;
import com.crediflow.application.service.LoanApplicationService;
import com.crediflow.application.feign.UserClient;
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
    public Result<Page<LoanApplication>> listApplications(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) String phone) {
        
        LambdaQueryWrapper<LoanApplication> queryWrapper = new LambdaQueryWrapper<>();

        if (phone != null && !phone.trim().isEmpty()) {
            Result<Long> userRes = userClient.getUserIdByPhone(phone);
            if (userRes == null || userRes.getData() == null) {
                // 如果找不到对应的用户，直接返回空分页
                return Result.success(new Page<>(current, size));
            }
            queryWrapper.eq(LoanApplication::getUserId, userRes.getData());
        }

        if (startTime != null) {
            queryWrapper.ge(LoanApplication::getCreatedAt, startTime);
        }
        if (endTime != null) {
            queryWrapper.le(LoanApplication::getCreatedAt, endTime);
        }
        queryWrapper.orderByDesc(LoanApplication::getCreatedAt);

        Page<LoanApplication> page = new Page<>(current, size);
        return Result.success(loanApplicationService.page(page, queryWrapper));
    }
}
