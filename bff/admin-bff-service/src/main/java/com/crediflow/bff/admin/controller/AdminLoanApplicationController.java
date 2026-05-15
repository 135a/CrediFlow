package com.crediflow.bff.admin.controller;

import com.crediflow.bff.admin.feign.LoanApplicationAdminClient;
import com.crediflow.bff.admin.dto.AdminReviewRequest;
import com.crediflow.common.web.Result;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * BFF 层：管理员贷款申请控制层。
 * 负责接收管理员前端页面的请求，调用底层的贷款服务进行状态查询和人工审核。
 */
@RestController
@RequestMapping("/api/admin/loan-application")
public class AdminLoanApplicationController {

    private static final Logger log = LoggerFactory.getLogger(AdminLoanApplicationController.class);

    @Autowired
    private LoanApplicationAdminClient loanApplicationAdminClient;

    /**
     * 获取待人工审核的贷款申请列表。
     * @param page 页码
     * @param size 每页数量
     * @return 分页的贷款申请列表
     */
    @GetMapping("/pending-list")
    public Result<Map<String, Object>> getPendingList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return loanApplicationAdminClient.listApplications(page, size, "PENDING_MANUAL_REVIEW");
    }

    /**
     * 获取指定 ID 的贷款申请详情。
     * @param id 贷款申请 ID
     * @return 贷款申请详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getApplication(@PathVariable("id") Long id) {
        return loanApplicationAdminClient.getApplication(id);
    }

    /**
     * 提交人工审核结果。
     * @param id 贷款申请 ID
     * @param req 审核请求对象，包含审核动作（APPROVE/REJECT）和原因
     * @param adminId 操作的管理员 ID（必须通过网关校验传递）
     * @return 操作结果
     */
    @PostMapping("/{id}/review")
    public Result<Void> reviewApplication(@PathVariable("id") Long id,
                                          @RequestBody AdminReviewRequest req,
                                          @RequestHeader(value = "X-Admin-Id") Long adminId) {
        if (adminId == null) {
            log.warn("Attempt to review application {} without X-Admin-Id", id);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing X-Admin-Id");
        }
        try {
            log.info("Admin {} reviewing application {} with action {}", adminId, id, req.getAction());
            return loanApplicationAdminClient.manualReview(id, req.getAction(), req.getReason(), adminId);
        } catch (Exception e) {
            log.error("Failed to review application {}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Review failed: " + e.getMessage());
        }
    }
}
