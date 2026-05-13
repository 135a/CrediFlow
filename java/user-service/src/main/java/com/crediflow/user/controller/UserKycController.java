package com.crediflow.user.controller;

import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.user.dto.UserKycStatusView;
import com.crediflow.user.dto.UserKycStep2Request;
import com.crediflow.user.dto.UserKycStep2Response;
import com.crediflow.user.entity.UserKyc;
import com.crediflow.user.realname.RealnameStatus;
import com.crediflow.user.realname.service.RealnameVerificationService;
import com.crediflow.user.service.UserKycService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/app/user/kyc")
public class UserKycController {

    @Autowired
    private UserKycService userKycService;

    @Autowired
    private RealnameVerificationService realnameVerificationService;

    /**
     * KYC v2 接管开关；默认 true，对旧 step1/2/3 直接返回 410 语义，
     * 应急回滚时可在 Nacos 切回 false 恢复旧链路。
     */
    @Value("${crediflow.kyc.use-v2:true}")
    private boolean useV2;

    private void rejectIfV2Active() {
        if (useV2) {
            throw new BusinessException(ErrorCode.KYC_LEGACY_API_GONE,
                    ErrorCode.KYC_LEGACY_API_GONE.getMessage());
        }
    }

    @PostMapping("/step1")
    public Result<Void> step1(@RequestParam Long userId, @RequestBody UserKyc kycData) {
        rejectIfV2Active();
        UserKyc kyc = userKycService.getByUserId(userId);
        if (kyc == null) {
            kyc = new UserKyc();
            kyc.setUserId(userId);
            kyc.setStepStatus(0);
            kyc.setRealnameStatus(RealnameStatus.NOT_SUBMITTED.name());
            kyc.setCreatedAt(new Date());
        }
        kyc.setMonthlyIncome(kycData.getMonthlyIncome());
        kyc.setBirthDate(kycData.getBirthDate());
        kyc.setResidence(kycData.getResidence());
        kyc.setOccupation(kycData.getOccupation());

        if (kyc.getStepStatus() < 1) {
            kyc.setStepStatus(1);
        }
        kyc.setUpdatedAt(new Date());
        userKycService.saveOrUpdate(kyc);
        return Result.success();
    }

    @PostMapping("/step2")
    public Result<Map<String, Object>> step2(
            @RequestParam Long userId,
            @RequestBody UserKycStep2Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        rejectIfV2Active();
        UserKycStep2Response resp =
                realnameVerificationService.submitStep2(userId, request.getRealName(), request.getIdCardNo(), idempotencyKey);
        return Result.success(resp.toMap());
    }

    @PostMapping("/step3")
    public Result<Void> step3(@RequestParam Long userId, @RequestBody UserKyc kycData) {
        rejectIfV2Active();
        UserKyc kyc = userKycService.getByUserId(userId);
        if (kyc == null || kyc.getStepStatus() == null || kyc.getStepStatus() < 2) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先完成步骤二");
        }
        if (!RealnameStatus.VERIFIED.name().equals(kyc.getRealnameStatus())) {
            throw new BusinessException(ErrorCode.REALNAME_NOT_VERIFIED, ErrorCode.REALNAME_NOT_VERIFIED.getMessage());
        }

        kyc.setPaymentMethod(kycData.getPaymentMethod());
        kyc.setPaymentAccount(kycData.getPaymentAccount());
        kyc.setStepStatus(3);
        kyc.setUpdatedAt(new Date());
        userKycService.updateById(kyc);

        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus(@RequestParam Long userId) {
        UserKyc kyc = userKycService.getByUserId(userId);
        return Result.success(toView(kyc).toMap());
    }

    private static UserKycStatusView toView(UserKyc k) {
        UserKycStatusView v = new UserKycStatusView();
        if (k == null) {
            v.setStepStatus(0);
            v.setRealnameStatus(RealnameStatus.NOT_SUBMITTED.name());
            return v;
        }
        v.setUserId(k.getUserId());
        v.setStepStatus(k.getStepStatus());
        v.setRealnameStatus(
                k.getRealnameStatus() == null ? RealnameStatus.NOT_SUBMITTED.name() : k.getRealnameStatus());
        v.setIdCardMask(k.getIdCardMask());
        v.setMonthlyIncome(k.getMonthlyIncome());
        v.setBirthDate(k.getBirthDate());
        v.setResidence(k.getResidence());
        v.setOccupation(k.getOccupation());
        v.setRealName(k.getRealName());
        v.setAge(k.getAge());
        v.setPaymentMethod(k.getPaymentMethod());
        v.setPaymentAccount(k.getPaymentAccount());
        return v;
    }
}
