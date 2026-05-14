package com.crediflow.user.face.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.user.entity.User;
import com.crediflow.user.face.config.FaceVerifyProperties;
import com.crediflow.user.face.entity.FaceVerifyLog;
import com.crediflow.user.face.mapper.FaceVerifyLogMapper;
import com.crediflow.user.face.model.FaceSubmitReceipt;
import com.crediflow.user.face.model.FaceVerifyCommand;
import com.crediflow.user.face.provider.MockFaceVerifyProvider;
import com.crediflow.user.face.provider.RoutingFaceVerifyProvider;
import com.crediflow.user.face.spi.FaceVerifyProvider;
import com.crediflow.user.face.support.FaceIdempotencyStore;
import com.crediflow.user.kyc.dto.KycStep2Response;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import com.crediflow.user.kyc.messaging.KycPassedEventPublisher;
import com.crediflow.user.mapper.UserMapper;
import com.crediflow.user.realname.RealnameStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * 人脸实人核验主流程：Mock / 白名单 / HTTP 三路径；中间态 + 幂等 + 流水落库 + 终态事件投递。
 */
@Service
public class FaceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(FaceVerificationService.class);

    private final FaceVerifyProperties properties;
    private final RoutingFaceVerifyProvider router;
    private final FaceVerifyLogMapper logMapper;
    private final UserKycV2Mapper kycV2Mapper;
    private final UserMapper userMapper;
    private final FaceIdempotencyStore idempotencyStore;
    private final KycPassedEventPublisher passedPublisher;

    public FaceVerificationService(FaceVerifyProperties properties,
                                   RoutingFaceVerifyProvider router,
                                   FaceVerifyLogMapper logMapper,
                                   UserKycV2Mapper kycV2Mapper,
                                   UserMapper userMapper,
                                   FaceIdempotencyStore idempotencyStore,
                                   KycPassedEventPublisher passedPublisher) {
        this.properties = properties;
        this.router = router;
        this.logMapper = logMapper;
        this.kycV2Mapper = kycV2Mapper;
        this.userMapper = userMapper;
        this.idempotencyStore = idempotencyStore;
        this.passedPublisher = passedPublisher;
    }

    @Transactional
    public KycStep2Response submit(long userId, String activeFaceToken, String idempotencyKey) {
        UserKycV2 kyc = kycV2Mapper.selectOne(
                new LambdaQueryWrapper<UserKycV2>().eq(UserKycV2::getUserId, userId));
        if (kyc == null || !RealnameStatus.VERIFIED.name().equals(kyc.getRealnameStatus())) {
            throw new BusinessException(ErrorCode.REALNAME_NOT_VERIFIED,
                    ErrorCode.REALNAME_NOT_VERIFIED.getMessage());
        }
        if (RealnameStatus.VERIFIED.name().equals(kyc.getFaceStatus()) && Boolean.TRUE.equals(kyc.getKycPassed())) {
            return new KycStep2Response("VERIFIED", kyc.getFaceProviderBizNo(),
                    safe(channelFromKyc(kyc), "HTTP"), true);
        }

        if (!idempotencyStore.tryAcquireClientIdempotency(userId, idempotencyKey,
                Duration.ofSeconds(Math.max(60, properties.getProcessingTtlSeconds())))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请勿重复提交人脸核验");
        }

        String bizNo = "FACE-" + UUID.randomUUID().toString().replace("-", "");
        if (shouldShortCircuit(userId, kyc)) {
            String channel = isPhoneWhitelisted(userId) || isFingerprintWhitelisted(kyc)
                    ? "WHITELIST" : "MOCK";
            return shortCircuitSuccess(userId, kyc, bizNo, channel);
        }

        FaceVerifyProvider provider = router.resolve();
        FaceSubmitReceipt receipt = provider.submit(
                new FaceVerifyCommand(userId, activeFaceToken, bizNo, idempotencyKey));
        if (!receipt.accepted()) {
            throw new BusinessException(ErrorCode.KYC_FACE_RETRYABLE,
                    ErrorCode.KYC_FACE_RETRYABLE.getMessage());
        }

        FaceVerifyLog logRow = new FaceVerifyLog();
        logRow.setUserId(userId);
        logRow.setProviderId(provider.providerId());
        logRow.setProviderBizNo(bizNo);
        logRow.setProviderTxnNo(receipt.upstreamTxnNo());
        logRow.setStatus("PROCESSING");
        logRow.setChannel("HTTP");
        logRow.setCreatedAt(new Date());
        logMapper.insert(logRow);

        kyc.setFaceStatus("PROCESSING");
        kyc.setFaceProviderId(provider.providerId());
        kyc.setFaceProviderBizNo(bizNo);
        kyc.setUpdatedAt(new Date());
        kycV2Mapper.updateById(kyc);

        idempotencyStore.writeState(userId, bizNo, "PROCESSING",
                Duration.ofSeconds(Math.max(60, properties.getProcessingTtlSeconds())));

        return new KycStep2Response("PROCESSING", bizNo, "HTTP", false);
    }

    private boolean shouldShortCircuit(long userId, UserKycV2 kyc) {
        return properties.getVerify().isMock()
                || isPhoneWhitelisted(userId)
                || isFingerprintWhitelisted(kyc);
    }

    private boolean isPhoneWhitelisted(long userId) {
        if (properties.getVerify().getWhitelist().getPhones().isEmpty()) {
            return false;
        }
        User u = userMapper.selectById(userId);
        if (u == null || u.getPhone() == null) {
            return false;
        }
        return properties.getVerify().getWhitelist().getPhones().contains(u.getPhone());
    }

    private boolean isFingerprintWhitelisted(UserKycV2 kyc) {
        if (kyc.getIdCardFingerprint() == null) {
            return false;
        }
        return properties.getVerify().getWhitelist().getFingerprints().contains(kyc.getIdCardFingerprint());
    }

    private KycStep2Response shortCircuitSuccess(long userId, UserKycV2 kyc, String bizNo, String channel) {
        Date now = new Date();
        FaceVerifyLog logRow = new FaceVerifyLog();
        logRow.setUserId(userId);
        logRow.setProviderId(MockFaceVerifyProvider.ID);
        logRow.setProviderBizNo(bizNo);
        logRow.setProviderTxnNo(channel + "-" + bizNo);
        logRow.setStatus("SUCCESS");
        logRow.setChannel(channel);
        logRow.setCreatedAt(now);
        logRow.setCallbackReceivedAt(now);
        logMapper.insert(logRow);

        kyc.setFaceStatus(RealnameStatus.VERIFIED.name());
        kyc.setFaceProviderId(MockFaceVerifyProvider.ID);
        kyc.setFaceProviderBizNo(bizNo);
        kyc.setFaceProviderTxnNo(logRow.getProviderTxnNo());
        kyc.setFaceVerifiedAt(now);
        kyc.setFaceFailureCode(null);
        boolean wasPassed = Boolean.TRUE.equals(kyc.getKycPassed());
        if (RealnameStatus.VERIFIED.name().equals(kyc.getRealnameStatus())) {
            kyc.setKycPassed(Boolean.TRUE);
            kyc.setKycPassedAt(now);
        }
        kyc.setUpdatedAt(now);
        kycV2Mapper.updateById(kyc);

        if (!wasPassed && Boolean.TRUE.equals(kyc.getKycPassed())) {
            passedPublisher.publish(userId, kyc.getRealnameProviderTxnNo(),
                    kyc.getFaceProviderTxnNo(), kyc.getIdCardMask());
        }
        idempotencyStore.writeState(userId, bizNo, "VERIFIED",
                Duration.ofSeconds(properties.getIdempotencyTtlSeconds()));
        log.info("[face] short-circuit success userId={} channel={} bizNo={}", userId, channel, bizNo);
        return new KycStep2Response("VERIFIED", bizNo, channel, Boolean.TRUE.equals(kyc.getKycPassed()));
    }

    private String channelFromKyc(UserKycV2 kyc) {
        return kyc.getFaceProviderId();
    }

    private static String safe(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
