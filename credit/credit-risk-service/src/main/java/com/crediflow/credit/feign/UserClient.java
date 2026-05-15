package com.crediflow.credit.feign;

import com.crediflow.common.api.user.UserEligibilityResponse;
import com.crediflow.common.api.user.UserKycStatusResponse;
import com.crediflow.common.web.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign客户端接口，用于与用户服务进行通信
 * 通过@FeignClient注解指定服务名称为"user-service"
 */
@FeignClient(name = "user-service")
public interface UserClient {

    /**
     * 获取用户KYC状态信息
     * @param userId 用户ID
     * @return 返回包含用户KYC状态信息的响应结果
     */
    @GetMapping("/api/app/user/kyc/status")
    Result<UserKycStatusResponse> getKycStatus(@RequestParam("userId") Long userId);

    /**
     * 根据手机号获取用户ID
     * @param phone 手机号码
     * @return 返回包含用户ID的响应结果
     */
    @GetMapping("/api/internal/user/by-phone")
    Result<Long> getUserIdByPhone(@RequestParam("phone") String phone);

    /**
     * 获取用户资格信息
     * @param userId 用户ID
     * @return 返回包含用户资格信息的响应结果
     */
    @GetMapping("/api/internal/user/eligibility")
    Result<UserEligibilityResponse> getEligibility(@RequestParam("userId") Long userId);

    /**
     * 初始化人脸活体检测
     * @param userId 用户ID
     * @param bizScene 业务场景
     * @param callbackUrl 回调地址
     * @return 返回初始化结果的响应
     */
    @PostMapping("/api/internal/user/face/init")
    Result<String> initFaceLiveness(@RequestParam("userId") Long userId,
                                    @RequestParam("bizScene") String bizScene,
                                    @RequestParam("callbackUrl") String callbackUrl);
}
