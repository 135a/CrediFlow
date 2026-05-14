package com.crediflow.credit.controller;

import com.crediflow.common.auth.annotation.Inner;
import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 信贷控制器类
 * 提供信贷申请、查询、额度管理等相关功能的REST API接口
 */
@RestController
@RequestMapping("/api/app/credit")
public class CreditController {

    // 自动注入信贷服务
    @Autowired
    private CreditService creditService;
    
    // 自动注入信贷申请服务
    @Autowired
    private com.crediflow.credit.service.CreditApplicationService creditApplicationService;
    
    // 自动注入用户信贷额度数据访问层
    @Autowired
    private com.crediflow.credit.mapper.UserCreditQuotaMapper userCreditQuotaMapper;

    /**
     * 信贷申请接口
     * @param userId 用户ID，从请求头中获取
     * @return 返回申请结果
     */
    @PostMapping("/apply")
    public Result<com.crediflow.credit.entity.CreditApplication> applyCredit(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(creditService.applyCredit(userId));
    }

    /**
     * 获取活跃信贷信息接口
     * @param userId 用户ID，从请求头中获取
     * @return 返回活跃信贷信息
     */
    @GetMapping("/active")
    public Result<CreditResult> getActiveCredit(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(creditService.getActiveCredit(userId));
    }

    /**
     * 内部接口：获取活跃信贷信息
     * @param userId 用户ID，从请求参数中获取
     * @return 返回活跃信贷信息
     */
    @Inner
    @GetMapping("/internal/active")
    public Result<CreditResult> getActiveCreditInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getActiveCredit(userId));
    }

    /**
     * 内部接口：申请信贷
     * @param userId 用户ID，从请求参数中获取
     * @return 返回申请结果，包含申请ID和状态
     */
    @Inner
    @PostMapping("/internal/apply")
    public Result<java.util.Map<String, Object>> applyCreditInternal(@RequestParam("userId") Long userId) {
        com.crediflow.credit.entity.CreditApplication app = creditService.applyCredit(userId);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("applicationId", app.getId());
        map.put("status", app.getStatus());
        return Result.success(map);
    }

    /**
     * 内部接口：获取信贷状态
     * @param userId 用户ID，从请求参数中获取
     * @return 返回信贷状态信息，包含状态、申请ID和是否需要二次人脸识别
     */
    @Inner
    @GetMapping("/internal/status")
    public Result<java.util.Map<String, Object>> getCreditStatusInternal(@RequestParam("userId") Long userId) {
        // 创建查询条件，查询用户最新的信贷申请
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
    
    /**
     * 内部接口：获取信贷额度
     * @param userId 用户ID，从请求参数中获取
     * @return 返回信贷额度信息，包含总额度、可用额度和已用额度
     */
    @Inner
    @GetMapping("/internal/quota")
    public Result<java.util.Map<String, Object>> getCreditQuotaInternal(@RequestParam("userId") Long userId) {
        // 创建查询条件，查询用户最新的信贷额度信息
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
    
    /**
     * 内部接口：获取上次审核结果
     * @param userId 用户ID，从请求参数中获取
     * @return 返回上次审核结果，包含状态、审核原因和安全洞察
     */
    @Inner
    @GetMapping("/internal/last-result")
    public Result<java.util.Map<String, Object>> getLastResultInternal(@RequestParam("userId") Long userId) {
        // 创建查询条件，查询用户最新的信贷申请
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.CreditApplication> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(com.crediflow.credit.entity.CreditApplication::getUserId, userId)
             .orderByDesc(com.crediflow.credit.entity.CreditApplication::getCreatedAt)
             .last("LIMIT 1");
             
        com.crediflow.credit.entity.CreditApplication app = creditApplicationService.getOne(query);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        if (app != null) {
            map.put("status", app.getStatus());
            map.put("auditReason", app.getAuditReason()); // Internal reason, BFF can map it
            // 7.2 requirement userSafeInsight
            map.put("userSafeInsight", app.getUserSafeInsight() != null ? app.getUserSafeInsight() : "综合评估未通过，请保持良好信用记录后重试"); 
        }
        return Result.success(map);
    }
    
    // 自动注入信贷审核队列数据访问层
    @Autowired
    private com.crediflow.credit.mapper.CreditReviewQueueMapper creditReviewQueueMapper;
    
    /**
     * 内部接口：风险信号升级
     * @param signalData 包含用户ID、聊天记录、代理建议和风险类型的数据
     * @return 返回操作结果
     */
    @Inner
    @PostMapping("/internal/risk-signal/escalate")
    public Result<Void> escalateRiskSignal(@RequestBody java.util.Map<String, Object> signalData) {
        Long userId = Long.valueOf(signalData.get("userId").toString());
        java.util.List<String> chatLogs = (java.util.List<String>) signalData.get("relevantChatLogs");
        String suggestion = (String) signalData.get("agentSuggestions");
        String riskType = (String) signalData.get("riskType");
        
        // 创建信贷审核队列记录
        com.crediflow.credit.entity.CreditReviewQueue queue = new com.crediflow.credit.entity.CreditReviewQueue();
        queue.setUserId(userId);
        
        // Find active application or just latest
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.crediflow.credit.entity.CreditApplication> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(com.crediflow.credit.entity.CreditApplication::getUserId, userId)
             .orderByDesc(com.crediflow.credit.entity.CreditApplication::getCreatedAt)
             .last("LIMIT 1");
        com.crediflow.credit.entity.CreditApplication app = creditApplicationService.getOne(query);
        
        queue.setApplicationId(app != null ? app.getId() : 0L);
        queue.setRiskDetails("[\"对话意图预警：" + riskType + "\", \"相关聊天记录：" + chatLogs.toString() + "\"]");
        queue.setDefaultProbability(0.85); // Estimated high risk
        queue.setFraudProbability(0.50);
        queue.setAiSuggestion(suggestion);
        queue.setStatus("PENDING");
        queue.setCreatedAt(new java.util.Date());
        queue.setUpdatedAt(new java.util.Date());
        
        creditReviewQueueMapper.insert(queue);
        
        return Result.success();
    }

    @Autowired
    private com.crediflow.credit.service.scoring.CreditScoringEngine creditScoringEngine;

    @Inner
    @PostMapping("/internal/evaluate-loan")
    public Result<String> evaluateLoanRisk(@RequestBody java.util.Map<String, Object> req) {
        Long userId = Long.valueOf(req.get("userId").toString());
        
        // 2.2 实时规则校验管线
        // 1. 检查是否存在未结清的逾期借款 (Mock)
        boolean hasOverdue = false; // TODO: check actual overdue loans
        if (hasOverdue) {
            return Result.success("REJECTED");
        }
        
        // 2. 检查深夜高频异地 (Mock)
        java.time.LocalTime now = java.time.LocalTime.now();
        if (now.getHour() >= 1 && now.getHour() <= 4) {
            boolean highFrequency = false; // mock high frequency
            if (highFrequency) {
                // 边界风险转人工审核
                return Result.success("MANUAL_REVIEW");
            }
        }
        
        // 2.3 复用授信四维模型，加载借款专属高门槛阈值
        com.crediflow.credit.service.scoring.ScoreDetail detail = creditScoringEngine.calculateLoanScore(userId);
        if ("HIGH".equals(detail.getRiskLevel())) {
            // 高风险单据转人工审核复核，而非直接刚性拒绝
            return Result.success("MANUAL_REVIEW");
        }
        
        return Result.success(detail.getRiskLevel());
    }

    @Autowired
    private com.crediflow.credit.service.impl.ManualReviewAsyncService manualReviewAsyncService;

    @Inner
    @PostMapping("/internal/review/enqueue")
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
        
        // 触发 Python Agent 进行借款审核三件套生成
        manualReviewAsyncService.generateManualReviewAssistantWithScene(applicationId, userId, 50.0, "HIGH", sceneType); // mock score/risk
            
        return Result.success();
    }

    @Inner
    @PostMapping("/internal/quota/deduct")
    public Result<Void> deductQuota(@RequestBody java.util.Map<String, Object> req) {
        Long userId = Long.valueOf(req.get("userId").toString());
        java.math.BigDecimal amount = new java.math.BigDecimal(req.get("amount").toString());
        creditService.deductQuota(userId, amount);
        return Result.success();
    }
}
