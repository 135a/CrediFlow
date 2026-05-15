package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 内部信贷控制器类
 * 提供微服务间调用的REST API接口，受内网签名隔离保护
 */
@RestController
@RequestMapping("/api/internal/credit")
public class CreditInternalController {

    @Autowired
    private CreditService creditService;
    
    @Autowired
    private com.crediflow.credit.service.CreditApplicationService creditApplicationService;
    
    @Autowired
    private com.crediflow.credit.mapper.UserCreditQuotaMapper userCreditQuotaMapper;
    
    @Autowired
    private com.crediflow.credit.mapper.CreditReviewQueueMapper creditReviewQueueMapper;
    
    @Autowired
    private com.crediflow.credit.service.scoring.CreditScoringEngine creditScoringEngine;
    
    @Autowired
    private com.crediflow.credit.service.impl.ManualReviewAsyncService manualReviewAsyncService;

    @GetMapping("/active")
    public Result<CreditResult> getActiveCreditInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getActiveCredit(userId));
    }

    @PostMapping("/apply")
    public Result<java.util.Map<String, Object>> applyCreditInternal(@RequestParam("userId") Long userId) {
        com.crediflow.credit.entity.CreditApplication app = creditService.applyCredit(userId);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("applicationId", app.getId());
        map.put("status", app.getStatus());
        return Result.success(map);
    }

    @GetMapping("/status")
    public Result<java.util.Map<String, Object>> getCreditStatusInternal(@RequestParam("userId") Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.CreditApplication> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(com.crediflow.credit.entity.CreditApplication::getUserId, userId)
             .orderByDesc(com.crediflow.credit.entity.CreditApplication::getCreatedAt)
             .last("LIMIT 1");
             
        com.crediflow.credit.entity.CreditApplication app = creditApplicationService.getOne(query);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        if (app != null) {
            map.put("status", app.getStatus());
            map.put("applicationId", app.getId());
            map.put("secondaryFaceRequired", app.getSecondaryFaceRequired());
        } else {
            map.put("status", "NOT_APPLIED");
        }
        return Result.success(map);
    }
    
    @GetMapping("/quota")
    public Result<java.util.Map<String, Object>> getCreditQuotaInternal(@RequestParam("userId") Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.UserCreditQuota> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(com.crediflow.credit.entity.UserCreditQuota::getUserId, userId)
             .last("LIMIT 1");
        
        com.crediflow.credit.entity.UserCreditQuota quota = userCreditQuotaMapper.selectOne(query);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        if (quota != null) {
            map.put("totalAmount", quota.getTotalAmount());
            map.put("availableAmount", quota.getAvailableAmount());
            map.put("usedAmount", quota.getUsedAmount());
        }
        return Result.success(map);
    }
    
    @GetMapping("/last-result")
    public Result<java.util.Map<String, Object>> getLastResultInternal(@RequestParam("userId") Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.CreditApplication> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(com.crediflow.credit.entity.CreditApplication::getUserId, userId)
             .orderByDesc(com.crediflow.credit.entity.CreditApplication::getCreatedAt)
             .last("LIMIT 1");
             
        com.crediflow.credit.entity.CreditApplication app = creditApplicationService.getOne(query);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        if (app != null) {
            map.put("status", app.getStatus());
            map.put("auditReason", app.getAuditReason());
            map.put("userSafeInsight", app.getUserSafeInsight() != null ? app.getUserSafeInsight() : "综合评估未通过，请保持良好信用记录后重试"); 
        }
        return Result.success(map);
    }
    
    @PostMapping("/risk-signal/escalate")
    public Result<Void> escalateRiskSignal(@RequestBody java.util.Map<String, Object> signalData) {
        Long userId = Long.valueOf(signalData.get("userId").toString());
        java.util.List<String> chatLogs = (java.util.List<String>) signalData.get("relevantChatLogs");
        String suggestion = (String) signalData.get("agentSuggestions");
        String riskType = (String) signalData.get("riskType");
        
        com.crediflow.credit.entity.CreditReviewQueue queue = new com.crediflow.credit.entity.CreditReviewQueue();
        queue.setUserId(userId);
        
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.CreditApplication> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(com.crediflow.credit.entity.CreditApplication::getUserId, userId)
             .orderByDesc(com.crediflow.credit.entity.CreditApplication::getCreatedAt)
             .last("LIMIT 1");
        com.crediflow.credit.entity.CreditApplication app = creditApplicationService.getOne(query);
        
        queue.setApplicationId(app != null ? app.getId() : 0L);
        queue.setRiskDetails("[\"对话意图预警：" + riskType + "\", \"相关聊天记录：" + chatLogs.toString() + "\"]");
        queue.setDefaultProbability(0.85);
        queue.setFraudProbability(0.50);
        queue.setAiSuggestion(suggestion);
        queue.setStatus("PENDING");
        queue.setCreatedAt(new java.util.Date());
        queue.setUpdatedAt(new java.util.Date());
        
        creditReviewQueueMapper.insert(queue);
        
        return Result.success();
    }

    @PostMapping("/evaluate-loan")
    public Result<String> evaluateLoanRisk(@RequestBody java.util.Map<String, Object> req) {
        Long userId = Long.valueOf(req.get("userId").toString());
        
        boolean hasOverdue = false; // TODO: check actual overdue loans
        if (hasOverdue) {
            return Result.success("REJECTED");
        }
        
        java.time.LocalTime now = java.time.LocalTime.now();
        if (now.getHour() >= 1 && now.getHour() <= 4) {
            boolean highFrequency = false; // mock high frequency
            if (highFrequency) {
                return Result.success("MANUAL_REVIEW");
            }
        }
        
        com.crediflow.credit.service.scoring.ScoreDetail detail = creditScoringEngine.calculateLoanScore(userId);
        if ("HIGH".equals(detail.getRiskLevel())) {
            return Result.success("MANUAL_REVIEW");
        }
        
        return Result.success(detail.getRiskLevel());
    }

    @PostMapping("/review/enqueue")
    public Result<Void> enqueueLoanReview(@RequestBody java.util.Map<String, Object> req) {
        Long applicationId = Long.valueOf(req.get("applicationId").toString());
        Long userId = Long.valueOf(req.get("userId").toString());
        String sceneType = (String) req.getOrDefault("sceneType", "LOAN");
        
        com.crediflow.credit.entity.CreditReviewQueue queue = new com.crediflow.credit.entity.CreditReviewQueue();
        queue.setApplicationId(applicationId);
        queue.setUserId(userId);
        queue.setSceneType(sceneType);
        queue.setStatus("PENDING");
        queue.setCreatedAt(new java.util.Date());
        queue.setUpdatedAt(new java.util.Date());
        
        creditReviewQueueMapper.insert(queue);
        
        manualReviewAsyncService.generateManualReviewAssistantWithScene(applicationId, userId, 50.0, "HIGH", sceneType);
            
        return Result.success();
    }
    
    @PostMapping("/quota/deduct")
    public Result<Void> deductQuota(@RequestBody java.util.Map<String, Object> req) {
        Long userId = Long.valueOf(req.get("userId").toString());
        java.math.BigDecimal amount = new java.math.BigDecimal(req.get("amount").toString());
        creditService.deductQuota(userId, amount);
        return Result.success();
    }
}
