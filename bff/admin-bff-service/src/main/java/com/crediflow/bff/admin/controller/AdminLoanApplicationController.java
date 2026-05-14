package com.crediflow.bff.admin.controller;

import com.crediflow.bff.admin.feign.LoanApplicationAdminClient;
import com.crediflow.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/loan-application")
public class AdminLoanApplicationController {

    @Autowired
    private LoanApplicationAdminClient loanApplicationAdminClient;

    @GetMapping("/pending-list")
    public Result<Map<String, Object>> getPendingList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return loanApplicationAdminClient.listApplications(page, size, "PENDING_MANUAL_REVIEW");
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getApplication(@PathVariable("id") Long id) {
        return loanApplicationAdminClient.getApplication(id);
    }

    @PostMapping("/{id}/review")
    public Result<Void> reviewApplication(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> req,
                                          @RequestHeader(value = "X-Admin-Id", defaultValue = "1") Long adminId) {
        String action = (String) req.get("action");
        String reason = (String) req.get("reason");
        return loanApplicationAdminClient.manualReview(id, action, reason, adminId);
    }
}
