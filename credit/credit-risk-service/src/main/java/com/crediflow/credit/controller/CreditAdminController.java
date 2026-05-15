package com.crediflow.credit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.service.CreditApplicationService;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 信贷管理控制器
 * 提供信贷申请管理、人工审批等功能接口
 */
@RestController
@RequestMapping("/api/admin/credit")
public class CreditAdminController {

    /**
     * 自动注入信贷申请服务
     */
    @Autowired
    private CreditApplicationService creditApplicationService;

    /**
     * 获取信贷申请列表
     * @param current 当前页码，默认为1
     * @param size 每页大小，默认为10
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param phone 手机号（可选）
     * @return 返回分页的信贷申请列表结果
     */
    @GetMapping("/applications")
    public Result<Page<CreditApplication>> listApplications(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) String phone) {
        
        return Result.success(creditApplicationService.listApplications(current, size, startTime, endTime, phone));
    }

    /**
     * 自动注入信贷服务
     */
    @Autowired
    private CreditService creditService;

    /**
     * 手工审批信贷申请
     * @param id 信贷申请ID
     * @param remark 备注信息（可选）
     * @return 返回操作结果
     */
    @PostMapping("/application/{id}/approve")
    public Result<Void> manualApprove(@PathVariable("id") Long id, @RequestParam(required = false, defaultValue = "") String remark) {
        creditService.manualApprove(id, remark);
        return Result.success(null);
    }
    
    /**
     * 获取人工审核队列
     * @param current 当前页码，默认为1
     * @param size 每页大小，默认为10
     * @return 返回分页的审核队列结果
     */
    @GetMapping("/manual-review-queue")
    public Result<Page<com.crediflow.credit.entity.CreditReviewQueue>> listReviewQueue(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(creditService.listReviewQueue(current, size));
    }
}
