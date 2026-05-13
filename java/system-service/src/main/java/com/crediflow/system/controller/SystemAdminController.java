package com.crediflow.system.controller;

import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system")
public class SystemAdminController {

    @PostMapping("/credit/application/{id}/approve")
    public Result<Void> approveCredit(@PathVariable Long id, 
                                      @RequestHeader("X-User-Id") Long userId,
                                      @RequestHeader("X-User-Role") String userRole) {
        
        // RBAC Check
        if (!"ROLE_RISK_ADMIN".equals(userRole)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无权进行人工风控强行过审操作");
        }
        
        // 执行过审逻辑
        // creditRiskService.forceApprove(id, userId);
        return Result.success(null);
    }
}
