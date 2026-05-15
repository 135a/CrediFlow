package com.crediflow.user.kyc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.kyc.IdCardFingerprintCalculator;
import com.crediflow.user.eligibility.EligibilityChecker;
import com.crediflow.user.eligibility.model.EligibilityDecision;
import com.crediflow.user.eligibility.model.EligibilityOutcome;
import com.crediflow.user.eligibility.policy.AgeRangePolicy;
import com.crediflow.user.kyc.dto.KycStep1Response;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import com.crediflow.user.kyc.service.KycV2Service;
import com.crediflow.user.realname.RealnameStatus;
import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import com.crediflow.user.realname.provider.RealnameProvider;
import com.crediflow.user.realname.service.RealnameAuditService;
import com.crediflow.user.realname.util.IdCardMask;
import com.crediflow.user.realname.util.IdCardValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Date;

/**
 * KYC v2 step1：闸门 + 二要素降级，状态全部落 {@code cf_user_kyc_v2}，不再写旧 {@code cf_user_kyc}。
 */
@Service
public class KycV2ServiceImpl implements KycV2Service {

    private final EligibilityChecker eligibilityChecker;
    private final AgeRangePolicy agePolicy;
    private final RealnameProvider realnameProvider;
    private final RealnameProperties realnameProperties;
    private final RealnameAuditService auditService;
    private final UserKycV2Mapper kycV2Mapper;

    public KycV2ServiceImpl(EligibilityChecker eligibilityChecker,
                            AgeRangePolicy agePolicy,
                            RealnameProvider realnameProvider,
                            RealnameProperties realnameProperties,
                            RealnameAuditService auditService,
                            UserKycV2Mapper kycV2Mapper) {
        this.eligibilityChecker = eligibilityChecker;
        this.agePolicy = agePolicy;
        this.realnameProvider = realnameProvider;
        this.realnameProperties = realnameProperties;
        this.auditService = auditService;
        this.kycV2Mapper = kycV2Mapper;
    }

    @Override
    public KycStep1Response submitStep1(long userId, String realName, String idCardNo, String idempotencyKey) {
        String rn = realName == null ? "" : realName.trim();
        String idc = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
        if (!StringUtils.hasText(realName) || !StringUtils.hasText(idCardNo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "姓名与身份证号不能为空");
        }
        if (!IdCardValidator.isValid18(idCardNo)) {
            throw new BusinessException(ErrorCode.REALNAME_ID_CARD_INVALID,
                    ErrorCode.REALNAME_ID_CARD_INVALID.getMessage());
        }

        UserKycV2 existing = loadOrInit(userId);
        if (RealnameStatus.VERIFIED.name().equals(existing.getRealnameStatus())) {
            return toResponse(existing);
        }

        EligibilityOutcome outcome = eligibilityChecker.check(userId, realName, idCardNo);
        AgeRangePolicy.Result age = agePolicy.evaluate(idCardNo, LocalDate.now());
        Date now = new Date();
        existing.setEligibilityStatus(toEligibilityStatus(outcome.decision()));
        existing.setEligibilityDecidedAt(now);
        existing.setAgeAtSubmit(age.age() < 0 ? null : age.age());

        if (outcome.decision() != EligibilityDecision.PASS) {
            persist(existing);
            throw eligibilityException(outcome.decision());
        }

        String fingerprint = IdCardFingerprintCalculator.hmacSha256Hex(
                realnameProperties.getIdempotencySalt(), realName, idCardNo);
        RealnameVerifyCommand cmd = new RealnameVerifyCommand(userId, realName, idCardNo);
        long t0 = System.nanoTime();
        RealnameVerifyResult result;
        try {
            result = realnameProvider.verify(cmd);
        } catch (RuntimeException ex) {
            existing.setRealnameStatus(
                    StringUtils.hasText(existing.getRealnameStatus())
                            ? existing.getRealnameStatus()
                            : RealnameStatus.NOT_SUBMITTED.name());
            persist(existing);
            throw new BusinessException(ErrorCode.REALNAME_RETRY_LATER,
                    ErrorCode.REALNAME_RETRY_LATER.getMessage());
        }
        long durationMs = (System.nanoTime() - t0) / 1_000_000L;
        String channel = realnameProperties.isMockSuccess() ? "MOCK" : "HTTP";
        auditService.record(realnameProperties, cmd, result, durationMs, channel);

        if (result.retryable()) {
            persist(existing);
            throw new BusinessException(ErrorCode.REALNAME_RETRY_LATER,
                    safe(result.userMessageSummary(), ErrorCode.REALNAME_RETRY_LATER.getMessage()));
        }
        if (result.terminalFailure()) {
            existing.setRealnameStatus(RealnameStatus.FAILED.name());
            existing.setRealName(realName);
            existing.setIdCardNo(idCardNo);
            existing.setIdCardMask(IdCardMask.mask18(idCardNo));
            existing.setIdCardFingerprint(fingerprint);
            persist(existing);
            throw new BusinessException(ErrorCode.REALNAME_VERIFY_FAILED,
                    safe(result.userMessageSummary(), ErrorCode.REALNAME_VERIFY_FAILED.getMessage()));
        }
        if (!result.effectiveSuccess()) {
            persist(existing);
            throw new BusinessException(ErrorCode.REALNAME_RETRY_LATER,
                    ErrorCode.REALNAME_RETRY_LATER.getMessage());
        }

        existing.setRealName(realName);
        existing.setIdCardNo(idCardNo);
        existing.setIdCardMask(IdCardMask.mask18(idCardNo));
        existing.setIdCardFingerprint(fingerprint);
        existing.setRealnameStatus(RealnameStatus.VERIFIED.name());
        existing.setRealnameVerifiedAt(now);
        existing.setRealnameProviderTxnNo(result.providerTxnNo());
        recomputeKycPassed(existing, now);
        persist(existing);

        return toResponse(existing);
    }

    private UserKycV2 loadOrInit(long userId) {
        UserKycV2 row = kycV2Mapper.selectOne(
                new LambdaQueryWrapper<UserKycV2>().eq(UserKycV2::getUserId, userId));
        if (row != null) {
            return row;
        }
        UserKycV2 fresh = new UserKycV2();
        fresh.setUserId(userId);
        fresh.setEligibilityStatus("NOT_SUBMITTED");
        fresh.setRealnameStatus(RealnameStatus.NOT_SUBMITTED.name());
        fresh.setFaceStatus("NOT_SUBMITTED");
        fresh.setKycPassed(Boolean.FALSE);
        fresh.setCreatedAt(new Date());
        fresh.setUpdatedAt(new Date());
        return fresh;
    }

    private void persist(UserKycV2 row) {
        row.setUpdatedAt(new Date());
        if (row.getId() == null) {
            kycV2Mapper.insert(row);
        } else {
            kycV2Mapper.updateById(row);
        }
    }

    private void recomputeKycPassed(UserKycV2 row, Date now) {
        boolean passed = RealnameStatus.VERIFIED.name().equals(row.getRealnameStatus())
                && RealnameStatus.VERIFIED.name().equals(row.getFaceStatus());
        if (passed && !Boolean.TRUE.equals(row.getKycPassed())) {
            row.setKycPassed(Boolean.TRUE);
            row.setKycPassedAt(now);
        } else if (!passed) {
            row.setKycPassed(Boolean.FALSE);
        }
    }

    private static String toEligibilityStatus(EligibilityDecision d) {
        return switch (d) {
            case PASS -> "PASS";
            case REJECT_AGE -> "REJECTED_AGE";
            case REJECT_DUP -> "REJECTED_DUP";
            case REJECT_BLACKLIST -> "REJECTED_BLACKLIST";
            case REJECT_RATE_LIMIT -> "REJECTED_RATE_LIMIT";
        };
    }

    private static BusinessException eligibilityException(EligibilityDecision d) {
        return switch (d) {
            case REJECT_AGE -> new BusinessException(ErrorCode.KYC_AGE_NOT_ELIGIBLE,
                    ErrorCode.KYC_AGE_NOT_ELIGIBLE.getMessage());
            case REJECT_DUP -> new BusinessException(ErrorCode.KYC_ID_CARD_DUPLICATED,
                    ErrorCode.KYC_ID_CARD_DUPLICATED.getMessage());
            case REJECT_BLACKLIST -> new BusinessException(ErrorCode.KYC_BLOCKED_BY_RISK,
                    ErrorCode.KYC_BLOCKED_BY_RISK.getMessage());
            case REJECT_RATE_LIMIT -> new BusinessException(ErrorCode.KYC_ELIGIBILITY_RATE_LIMIT,
                    ErrorCode.KYC_ELIGIBILITY_RATE_LIMIT.getMessage());
            case PASS -> new BusinessException(ErrorCode.BUSINESS_ERROR, "unreachable");
        };
    }

    private KycStep1Response toResponse(UserKycV2 row) {
        return new KycStep1Response(
                row.getIdCardMask(),
                row.getRealnameStatus(),
                row.getRealnameProviderTxnNo(),
                row.getEligibilityStatus());
    }

    private static String safe(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
