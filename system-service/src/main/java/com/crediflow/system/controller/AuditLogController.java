package com.crediflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crediflow.common.web.Result;
import com.crediflow.system.entity.AuditLog;
import com.crediflow.system.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit-log")
public class AuditLogController {
    
    @Autowired
    private AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('sys:audit:query')")
    @GetMapping("/page")
    public Result<Page<AuditLog>> page(@RequestParam(defaultValue = "1") long current, 
                                       @RequestParam(defaultValue = "10") long size) {
        return Result.success(auditLogService.page(new Page<>(current, size)));
    }
}
