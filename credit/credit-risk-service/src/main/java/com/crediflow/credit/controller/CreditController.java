package com.crediflow.credit.controller;
import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditResult;
import com.crediflow.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 信贷控制器类
 * 提供信贷申请、查询等相关功能的对外REST API接口
 */
@RestController
@RequestMapping("/api/app/credit")
public class CreditController {

    // 自动注入信贷服务
    @Autowired
    private CreditService creditService;
    
    /**
     * 信贷申请接口
     * 处理用户的信贷申请请求，通过用户ID创建新的信贷申请记录
     * @param userId 用户ID，从请求头中获取
     * @return 返回申请结果，包含申请成功后的信贷申请信息
     */
    @PostMapping("/apply")
    public Result<com.crediflow.credit.entity.CreditApplication> applyCredit(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(creditService.applyCredit(userId));
    }

    /**
     * 获取活跃信贷信息接口
     * 根据用户ID查询当前活跃的信贷信息
     * @param userId 用户ID，从请求头中获取
     * @return 返回活跃信贷信息，包含信贷详情、状态等相关信息
     */
    @GetMapping("/active")
    public Result<CreditResult> getActiveCredit(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(creditService.getActiveCredit(userId));
    }
}
