package com.crediflow.user.face.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.user.face.config.FaceVerifyProperties;
import com.crediflow.user.face.entity.FaceVerifyLog;
import com.crediflow.user.face.mapper.FaceVerifyLogMapper;
import com.crediflow.user.face.model.CallbackParseResult;
import com.crediflow.user.face.provider.RoutingFaceVerifyProvider;
import com.crediflow.user.face.spi.FaceVerifyProvider;
import com.crediflow.user.face.support.FaceIdempotencyStore;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import com.crediflow.user.kyc.messaging.KycPassedEventPublisher;
import com.crediflow.user.realname.RealnameStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;

/**
 * 处理人脸厂商异步回调：双层验签 + 幂等 + 落流水 + 重算 kyc_passed + 投 KYC_PASSED_EVENT。
 */
@Service
public class FaceCallbackService {

    private static final Logger log = LoggerFactory.getLogger(FaceCallbackService.class);

    private final RoutingFaceVerifyProvider router;
    private final FaceVerifyLogMapper logMapper;
    private final UserKycV2Mapper kycV2Mapper;
    private final FaceIdempotencyStore idempotencyStore;
    private final FaceVerifyProperties properties;
    private final KycPassedEventPublisher passedPublisher;

    public FaceCallbackService(RoutingFaceVerifyProvider router,
                               FaceVerifyLogMapper logMapper,
                               UserKycV2Mapper kycV2Mapper,
                               FaceIdempotencyStore idempotencyStore,
                               FaceVerifyProperties properties,
                               KycPassedEventPublisher passedPublisher) {
        this.router = router;
        this.logMapper = logMapper;
        this.kycV2Mapper = kycV2Mapper;
        this.idempotencyStore = idempotencyStore;
        this.properties = properties;
        this.passedPublisher = passedPublisher;
    }

    public enum Outcome { OK, SIGNATURE_INVALID, IDEMPOTENT_HIT, ORPHAN }

    @Transactional
    public Outcome handle(byte[] rawBody, Map<String, String> headers) {
        FaceVerifyProvider provider = router.resolve();
        boolean signatureValid;
        try {
            signatureValid = provider.verifySignature(rawBody, headers);
        } catch (RuntimeException ex) {
            log.warn("[face-callback] verifySignature threw: {}", ex.toString());
            return Outcome.SIGNATURE_INVALID;
        }
        if (!signatureValid) {
            log.warn("[face-callback] signature invalid providerId={}", provider.providerId());
            return Outcome.SIGNATURE_INVALID;
        }

        CallbackParseResult parsed = provider.parseCallback(rawBody, headers);
        String bizNo = parsed.providerBizNo();
        String txnNo = parsed.providerTxnNo();
        if (txnNo == null || txnNo.isBlank()) {
            txnNo = bizNo;
        }

        if (!idempotencyStore.tryAcquireCallback(provider.providerId(), txnNo,
                Duration.ofSeconds(properties.getIdempotencyTtlSeconds()))) {
            log.info("[face-callback] idempotent hit providerId={} txnNo={}", provider.providerId(), txnNo);
            return Outcome.IDEMPOTENT_HIT;
        }

        FaceVerifyLog row = null;
        if (bizNo != null && !bizNo.isBlank()) {
            row = logMapper.selectOne(new LambdaQueryWrapper<FaceVerifyLog>()
                    .eq(FaceVerifyLog::getProviderBizNo, bizNo));
        }
        if (row == null) {
            log.warn("[face-callback] orphan bizNo={} txnNo={} providerId={}", bizNo, txnNo, provider.providerId());
            return Outcome.ORPHAN;
        }

        boolean success = "SUCCESS".equalsIgnoreCase(parsed.terminal());
        Date now = new Date();
        row.setStatus(success ? "SUCCESS" : "FAILED");
        row.setProviderTxnNo(txnNo);
        row.setFailureCode(parsed.failureCode());
        row.setCallbackReceivedAt(now);
        row.setPayloadDigest(digest(rawBody));
        logMapper.updateById(row);

        UserKycV2 kyc = kycV2Mapper.selectOne(new LambdaQueryWrapper<UserKycV2>()
                .eq(UserKycV2::getUserId, row.getUserId()));
        if (kyc == null) {
            return Outcome.ORPHAN;
        }
        if (success) {
            kyc.setFaceStatus(RealnameStatus.VERIFIED.name());
            kyc.setFaceProviderTxnNo(txnNo);
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
                passedPublisher.publish(row.getUserId(), kyc.getRealnameProviderTxnNo(),
                        kyc.getFaceProviderTxnNo(), kyc.getIdCardMask());
            }
            idempotencyStore.writeState(row.getUserId(), bizNo, "VERIFIED",
                    Duration.ofSeconds(properties.getIdempotencyTtlSeconds()));
        } else {
            kyc.setFaceStatus(RealnameStatus.FAILED.name());
            kyc.setFaceFailureCode(truncate(parsed.failureCode(), 64));
            kyc.setUpdatedAt(now);
            kycV2Mapper.updateById(kyc);
            idempotencyStore.writeState(row.getUserId(), bizNo, "FAILED",
                    Duration.ofSeconds(properties.getIdempotencyTtlSeconds()));
        }
        return Outcome.OK;
    }

    private static String digest(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(body)).substring(0, 32);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
