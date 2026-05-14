package com.crediflow.user.kyc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.web.Result;
import com.crediflow.user.face.service.FaceVerificationService;
import com.crediflow.user.kyc.dto.KycStatusResponse;
import com.crediflow.user.kyc.dto.KycStep1Request;
import com.crediflow.user.kyc.dto.KycStep1Response;
import com.crediflow.user.kyc.dto.KycStep2Request;
import com.crediflow.user.kyc.dto.KycStep2Response;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import com.crediflow.user.kyc.service.KycV2Service;
import com.crediflow.user.realname.RealnameStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * KYC v2 主流程：二要素 + 闸门 / 人脸实人 / 综合状态。
 */
@RestController
@RequestMapping("/api/app/user/kyc/v2")
public class UserKycV2Controller {

    private final KycV2Service kycV2Service;
    private final FaceVerificationService faceVerificationService;
    private final UserKycV2Mapper kycV2Mapper;

    public UserKycV2Controller(KycV2Service kycV2Service,
                               FaceVerificationService faceVerificationService,
                               UserKycV2Mapper kycV2Mapper) {
        this.kycV2Service = kycV2Service;
        this.faceVerificationService = faceVerificationService;
        this.kycV2Mapper = kycV2Mapper;
    }

    @PostMapping("/step1")
    public Result<KycStep1Response> step1(@RequestParam Long userId,
                                          @RequestBody KycStep1Request request,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(kycV2Service.submitStep1(userId,
                request == null ? null : request.getRealName(),
                request == null ? null : request.getIdCardNo(),
                idempotencyKey));
    }

    @PostMapping("/step2")
    public Result<KycStep2Response> step2(@RequestParam Long userId,
                                          @RequestBody KycStep2Request request,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(faceVerificationService.submit(userId,
                request == null ? null : request.getActiveFaceToken(),
                idempotencyKey));
    }

    @GetMapping("/status")
    public Result<KycStatusResponse> status(@RequestParam Long userId) {
        UserKycV2 row = kycV2Mapper.selectOne(
                new LambdaQueryWrapper<UserKycV2>().eq(UserKycV2::getUserId, userId));
        if (row == null) {
            return Result.success(new KycStatusResponse(
                    "NOT_SUBMITTED",
                    RealnameStatus.NOT_SUBMITTED.name(),
                    "NOT_SUBMITTED",
                    false,
                    null));
        }
        return Result.success(new KycStatusResponse(
                row.getEligibilityStatus(),
                row.getRealnameStatus(),
                row.getFaceStatus(),
                Boolean.TRUE.equals(row.getKycPassed()),
                row.getIdCardMask()));
    }
}
