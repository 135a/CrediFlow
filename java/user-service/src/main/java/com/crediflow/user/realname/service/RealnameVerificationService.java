package com.crediflow.user.realname.service;

import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.user.dto.UserKycStep2Response;
import com.crediflow.user.entity.UserKyc;
import com.crediflow.user.realname.RealnameStatus;
import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import com.crediflow.user.realname.provider.RealnameProvider;
import com.crediflow.user.realname.support.RealnameIdempotencyStore;
import com.crediflow.user.realname.support.RealnameRateLimiter;
import com.crediflow.user.realname.util.IdCardFingerprint;
import com.crediflow.user.realname.util.IdCardMask;
import com.crediflow.user.realname.util.IdCardValidator;
import com.crediflow.user.service.UserKycService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

@Service
public class RealnameVerificationService {

    private final RealnameProperties properties;
    private final RealnameProvider realnameProvider;
    private final RealnameRateLimiter rateLimiter;
    private final RealnameIdempotencyStore idempotencyStore;
    private final RealnameAuditService auditService;
    private final UserKycService userKycService;
    private final ObjectMapper objectMapper;

    public RealnameVerificationService(
            RealnameProperties properties,
            RealnameProvider realnameProvider,
            RealnameRateLimiter rateLimiter,
            RealnameIdempotencyStore idempotencyStore,
            RealnameAuditService auditService,
            UserKycService userKycService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.realnameProvider = realnameProvider;
        this.rateLimiter = rateLimiter;
        this.idempotencyStore = idempotencyStore;
        this.auditService = auditService;
        this.userKycService = userKycService;
        this.objectMapper = objectMapper;
    }

    public UserKycStep2Response submitStep2(long userId, String realName, String idCardNo, String idempotencyKey) {
        String rn = realName == null ? "" : realName.trim();
        String idc = idCardNo == null ? "" : idCardNo.trim().toUpperCase();
        if (!StringUtils.hasText(rn) || !StringUtils.hasText(idc)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "姓名与身份证号不能为空");
        }
        if (!IdCardValidator.isValid18(idc)) {
            throw new BusinessException(ErrorCode.REALNAME_ID_CARD_INVALID, "身份证号格式或校验位不正确");
        }

        if (!rateLimiter.tryAcquire(userId)) {
            throw new BusinessException(ErrorCode.REALNAME_RATE_LIMIT, ErrorCode.REALNAME_RATE_LIMIT.getMessage());
        }

        String fp = IdCardFingerprint.hmacHex(properties.getIdempotencySalt(), rn, idc);
        String idemRedisKey = buildIdempotencyRedisKey(userId, idempotencyKey, fp);
        String cached = idempotencyStore.get(idemRedisKey);
        if (cached != null) {
            try {
                Map<String, Object> m = objectMapper.readValue(cached, new TypeReference<>() {});
                return new UserKycStep2Response(
                        (String) m.get("idCardMask"),
                        (String) m.get("realnameStatus"),
                        (String) m.get("providerTxnNo"));
            } catch (Exception ignored) {
                // fall through to re-verify
            }
        }

        if (!properties.isMockSuccess()) {
            if (!properties.isEnabled()) {
                throw new BusinessException(ErrorCode.REALNAME_CONFIG_ERROR, "实名服务已禁用");
            }
            if (!StringUtils.hasText(properties.getBaseUrl())) {
                throw new BusinessException(ErrorCode.REALNAME_CONFIG_ERROR, "实名外呼地址未配置");
            }
        }

        UserKyc kyc = userKycService.getByUserId(userId);
        if (kyc == null || kyc.getStepStatus() == null || kyc.getStepStatus() < 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先完成步骤一");
        }

        RealnameVerifyCommand cmd = new RealnameVerifyCommand(userId, rn, idc);
        long t0 = System.nanoTime();
        RealnameVerifyResult result = realnameProvider.verify(cmd);
        long durationMs = (System.nanoTime() - t0) / 1_000_000L;
        String channel = properties.isMockSuccess() ? "MOCK" : "HTTP";
        auditService.record(properties, cmd, result, durationMs, channel);

        if (result.retryable()) {
            throw new BusinessException(
                    ErrorCode.REALNAME_RETRY_LATER,
                    result.userMessageSummary() == null ? ErrorCode.REALNAME_RETRY_LATER.getMessage() : result.userMessageSummary());
        }

        if (result.terminalFailure()) {
            kyc.setRealnameStatus(RealnameStatus.FAILED.name());
            kyc.setRealnameInternalReason(truncate(result.internalReasonCode(), 64));
            kyc.setUpdatedAt(new Date());
            userKycService.updateById(kyc);
            throw new BusinessException(
                    ErrorCode.REALNAME_VERIFY_FAILED,
                    result.userMessageSummary() == null ? ErrorCode.REALNAME_VERIFY_FAILED.getMessage() : result.userMessageSummary());
        }

        if (!result.effectiveSuccess()) {
            throw new BusinessException(ErrorCode.REALNAME_RETRY_LATER, ErrorCode.REALNAME_RETRY_LATER.getMessage());
        }

        kyc.setRealName(rn);
        kyc.setIdCardNo(idc);
        kyc.setIdCardMask(IdCardMask.mask18(idc));
        kyc.setIdCardFingerprint(fp);
        kyc.setRealnameStatus(RealnameStatus.VERIFIED.name());
        kyc.setRealnameVerifiedAt(new Date());
        kyc.setRealnameProviderTxnNo(result.providerTxnNo());
        kyc.setRealnameInternalReason(null);
        kyc.setFaceVerified(Boolean.FALSE);
        if (kyc.getStepStatus() < 2) {
            kyc.setStepStatus(2);
        }
        kyc.setUpdatedAt(new Date());
        userKycService.updateById(kyc);

        UserKycStep2Response resp =
                new UserKycStep2Response(kyc.getIdCardMask(), kyc.getRealnameStatus(), kyc.getRealnameProviderTxnNo());
        try {
            idempotencyStore.put(
                    idemRedisKey,
                    objectMapper.writeValueAsString(resp.toMap()),
                    Duration.ofSeconds(Math.max(1, properties.getIdempotencyTtlSeconds())));
        } catch (Exception ignored) {
            // 幂等缓存失败不阻断主流程
        }
        return resp;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String buildIdempotencyRedisKey(long userId, String idempotencyKey, String fingerprint) {
        if (StringUtils.hasText(idempotencyKey)) {
            return "realname:idem:hdr:" + userId + ":" + idempotencyKey.trim();
        }
        return "realname:idem:fp:" + userId + ":" + fingerprint;
    }
}
