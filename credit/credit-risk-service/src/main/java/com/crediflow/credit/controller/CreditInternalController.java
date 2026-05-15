package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.service.CreditApplicationService;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部信贷控制器类
 * 提供微服务间调用的REST API接口，受内网签名隔离保护
 */
@RestController
@RequestMapping("/api/internal/credit")  // 设置REST API的基础路径
public class CreditInternalController {

    @Autowired  // 自动注入CreditService服务
    private CreditService creditService;
    
    @Autowired  // 自动注入CreditApplicationService服务
    private CreditApplicationService creditApplicationService;



    /**
     * 获取用户的激活信贷信息
     * @param userId 用户ID
     * @return 返回包含激活信贷信息的Result对象
     */
    @GetMapping("/active")  // 处理GET请求，路径为"/active"
    public Result<CreditResult> getActiveCreditInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getActiveCredit(userId));
    }



    /**
     * 申请信贷
     * @param userId 用户ID
     * @return 返回包含申请ID和状态的Result对象
     */
    @PostMapping("/apply")  // 处理POST请求，路径为"/apply"
    public Result<Map<String, Object>> applyCreditInternal(@RequestParam("userId") Long userId) {
        CreditApplication app = creditService.applyCredit(userId);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("applicationId", app.getId());  // 存储申请ID
        map.put("status", app.getStatus() != null ? app.getStatus().getCode() : null);
        return Result.success(map);
    }



    /**
     * 获取信贷申请状态
     * @param userId 用户ID
     * @return 返回包含申请状态的Result对象
     */
    @GetMapping("/status")  // 处理GET请求，路径为"/status"
    public Result<Map<String, Object>> getCreditStatusInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditApplicationService.getLastApplicationStatus(userId));
    }
    


    /**
     * 获取信贷额度信息
     * @param userId 用户ID
     * @return 返回包含额度信息的Result对象
     */
    @GetMapping("/quota")  // 处理GET请求，路径为"/quota"
    public Result<java.util.Map<String, Object>> getCreditQuotaInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditService.getQuotaSummary(userId));
    }
    


    /**
     * 获取最新的信贷申请结果
     * @param userId 用户ID
     * @return 返回包含最新申请结果的Result对象
     */
    @GetMapping("/last-result")  // 处理GET请求，路径为"/last-result"
    public Result<java.util.Map<String, Object>> getLastResultInternal(@RequestParam("userId") Long userId) {
        return Result.success(creditApplicationService.getLastApplicationResult(userId));
    }
    


    /**
     * 上报风险信号
     * @param signalData 风险信号数据
     * @return 返回操作结果的Result对象
     */
    @PostMapping("/risk-signal/escalate")  // 处理POST请求，路径为"/risk-signal/escalate"
    public Result<Void> escalateRiskSignal(@RequestBody java.util.Map<String, Object> signalData) {
        creditService.escalateRiskSignal(signalData);
        return Result.success();
    }



    /**
     * 评估贷款风险
     * @param req 包含评估请求的数据
     * @return 返回评估结果的Result对象
     */
    @PostMapping("/evaluate-loan")  // 处理POST请求，路径为"/evaluate-loan"
    public Result<String> evaluateLoanRisk(@RequestBody java.util.Map<String, Object> req) {
        return Result.success(creditService.evaluateLoanRisk(req));
    }



    /**
     * 将贷款申请加入审核队列
     * @param req 包含申请数据的Map对象
     * @return 返回操作结果的Result对象
     */
    @PostMapping("/review/enqueue")  // 处理POST请求，路径为"/review/enqueue"
    public Result<Void> enqueueLoanReview(@RequestBody java.util.Map<String, Object> req) {
        creditService.enqueueLoanReview(req);
        return Result.success();
    }
    


    /**
     * 扣减信贷额度
     * @param req 包含用户ID和扣减金额的Map对象
     * @return 返回操作结果的Result对象
     */
    @PostMapping("/quota/deduct")  // 处理POST请求，路径为"/quota/deduct"
    public Result<Void> deductQuota(@RequestBody java.util.Map<String, Object> req) {  // 从请求中获取用户ID
        Long userId = Long.valueOf(req.get("userId").toString());  // 从请求中获取金额
        java.math.BigDecimal amount = new java.math.BigDecimal(req.get("amount").toString());  // 调用服务层扣减额度
        creditService.deductQuota(userId, amount);
        return Result.success();
    }
}
