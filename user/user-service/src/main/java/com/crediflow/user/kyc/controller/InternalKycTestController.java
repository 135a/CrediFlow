package com.crediflow.user.kyc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.user.face.entity.FaceVerifyLog;
import com.crediflow.user.face.mapper.FaceVerifyLogMapper;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import com.crediflow.user.kyc.messaging.KycPassedEventPublisher;
import com.crediflow.user.realname.RealnameStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.UUID;

/**
 * 非生产 KYC 后门接口：直接把指定 userId 置为 kyc_passed=1（仅用于联调）。
 * 生产 profile 下不会注册，外部请求 404。
 */
@RestController
@RequestMapping("/api/internal/test/kyc")
@Profile("!prod & !production")
public class InternalKycTestController {

    private static final Logger log = LoggerFactory.getLogger(InternalKycTestController.class);

    private final UserKycV2Mapper kycV2Mapper;
    private final FaceVerifyLogMapper logMapper;
    private final KycPassedEventPublisher publisher;

    public InternalKycTestController(UserKycV2Mapper kycV2Mapper,
                                     FaceVerifyLogMapper logMapper,
                                     KycPassedEventPublisher publisher) {
        this.kycV2Mapper = kycV2Mapper;
        this.logMapper = logMapper;
        this.publisher = publisher;
    }
    @PostMapping("/force-pass")
    public Result<String> forcePass(@RequestParam("userId") Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId required");
        }
        UserKycV2 kyc = kycV2Mapper.selectOne(
                new LambdaQueryWrapper<UserKycV2>().eq(UserKycV2::getUserId, userId));
        if (kyc == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户尚未发起 KYC v2，无法后门通过");
        }
        Date now = new Date();
        String bizNo = "BACKDOOR-" + UUID.randomUUID().toString().replace("-", "");
        FaceVerifyLog row = new FaceVerifyLog();
        row.setUserId(userId);
        row.setProviderId("backdoor");
        row.setProviderBizNo(bizNo);
        row.setProviderTxnNo("BACKDOOR-" + bizNo);
        row.setStatus("SUCCESS");
        row.setChannel("BACKDOOR");
        row.setCreatedAt(now);
        row.setCallbackReceivedAt(now);
        logMapper.insert(row);

        boolean wasPassed = Boolean.TRUE.equals(kyc.getKycPassed());
        kyc.setFaceStatus(RealnameStatus.VERIFIED.name());
        kyc.setFaceProviderId("backdoor");
        kyc.setFaceProviderBizNo(bizNo);
        kyc.setFaceProviderTxnNo(row.getProviderTxnNo());
        kyc.setFaceVerifiedAt(now);
        kyc.setFaceFailureCode(null);
        if (RealnameStatus.VERIFIED.name().equals(kyc.getRealnameStatus())) {
            kyc.setKycPassed(Boolean.TRUE);
            kyc.setKycPassedAt(now);
        }
        kyc.setUpdatedAt(now);
        kycV2Mapper.updateById(kyc);

        if (!wasPassed && Boolean.TRUE.equals(kyc.getKycPassed())) {
            publisher.publish(userId, kyc.getRealnameProviderTxnNo(),
                    kyc.getFaceProviderTxnNo(), kyc.getIdCardMask());
        }
        log.warn("[kyc-backdoor] userId={} force-pass executed (NON-PROD ONLY)", userId);
        return Result.success("OK");
    }
}
