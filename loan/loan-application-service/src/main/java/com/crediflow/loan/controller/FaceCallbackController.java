package com.crediflow.loan.controller;

import com.crediflow.loan.entity.LoanApplication;
import com.crediflow.loan.service.LoanApplicationService;
import com.crediflow.common.web.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.crediflow.loan.feign.CreditClient;
import com.crediflow.common.api.credit.LoanReviewEnqueueRequest;

@Slf4j
@RestController
@RequestMapping("/api/internal/app/loan-application/face")
public class FaceCallbackController {

    @Autowired
    private LoanApplicationService loanApplicationService;

    @Autowired
    private CreditClient creditClient;

    @PostMapping("/callback")
    public Result<Void> handleFaceCallback(@RequestBody FaceCallbackRequest request) {
        log.info("Received loan face callback for applicationId: {}, passed: {}", 
                 request.getApplicationId(), request.isPassed());
                 
        LoanApplication application = loanApplicationService.getById(request.getApplicationId());
        if (application == null || !"PENDING_FACE".equals(application.getStatus())) {
            log.warn("Invalid face callback or application state for id: {}", request.getApplicationId());
            return Result.success();
        }

        if (request.isPassed()) {
            if ("MEDIUM".equals(application.getRiskLevel())) {
                application.setStatus("CONTRACT_PROCESSING");
                loanApplicationService.updateById(application);
                loanApplicationService.approve(application.getId());
            } else if ("HIGH".equals(application.getRiskLevel())) {
                application.setStatus("PENDING_MANUAL");
                loanApplicationService.updateById(application);
                
                // 3.2 调用风控服务投入人工审核队列
                LoanReviewEnqueueRequest req = new LoanReviewEnqueueRequest();
                req.setApplicationId(application.getId());
                req.setUserId(application.getUserId());
                req.setSceneType("LOAN");
                creditClient.enqueueLoanReview(req);
            } else {
                application.setStatus("CONTRACT_PROCESSING");
                loanApplicationService.updateById(application);
                loanApplicationService.approve(application.getId());
            }
        } else {
            application.setStatus("REJECTED");
            loanApplicationService.updateById(application);
        }
        
        return Result.success();
    }

    @Data
    public static class FaceCallbackRequest {
        private String applicationId;
        private Long userId;
        private boolean passed;
        private String errorMessage;
    }
}
