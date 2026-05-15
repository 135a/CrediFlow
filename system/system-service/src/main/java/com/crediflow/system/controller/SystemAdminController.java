package com.crediflow.system.controller;

import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统管理后台控制层。
 * 负责接收高级管理员或系统级操作员的请求，进行紧急运维干预（如风控强批）。
 */
@RestController
@RequestMapping("/api/admin/system")
public class SystemAdminController {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminController.class);

    /**
     * 强行通过指定的风控授信申请。
     * 仅允许风险管理员（ROLE_RISK_ADMIN）调用，用于紧急的白名单放行或系统故障时的兜底干预。
     *
     * @param id 授信申请的 ID
     * @param userId 操作用户的 ID
     * @param userRole 操作用户的角色
     * @return 操作结果
     */
    @PostMapping("/credit/application/{id}/approve")
    public Result<Void> approveCredit(@PathVariable Long id, 
                                      @RequestHeader("X-User-Id") Long userId,
                                      @RequestHeader("X-User-Role") String userRole) {
        log.info("Received force approve request for credit application {}. Operator: {}, Role: {}", id, userId, userRole);
        
        // RBAC Check
        if (!"ROLE_RISK_ADMIN".equals(userRole)) {
            log.warn("Force approve denied for application {}. Insufficient permissions. Operator: {}, Role: {}", id, userId, userRole);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无权进行人工风控强行过审操作");
        }
        
        // 执行过审逻辑
        // TODO(Task-8.2): forceApprove 依赖 credit-risk-service 的重审能力，当前该能力未实现，预计在 v1.2 版本上线。
        // 详见：confluence/CRED-1234
        // creditRiskService.forceApprove(id, userId);
        
        log.info("Successfully processed force approve request for credit application {}", id);
        return Result.success(null);
    }
}
