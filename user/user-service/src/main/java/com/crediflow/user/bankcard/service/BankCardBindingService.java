package com.crediflow.user.bankcard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.kyc.IdCardFingerprintCalculator;
import com.crediflow.common.util.MaskingUtil;
import com.crediflow.user.bankcard.config.BankCardProperties;
import com.crediflow.user.bankcard.dto.BankCardView;
import com.crediflow.user.bankcard.entity.UserBankCard;
import com.crediflow.user.bankcard.mapper.UserBankCardMapper;
import com.crediflow.user.bankcard.model.BankCardVerifyCommand;
import com.crediflow.user.bankcard.model.BankCardVerifyResult;
import com.crediflow.user.bankcard.provider.RoutingBankCardProvider;
import com.crediflow.user.bankcard.spi.BankCardFourElementsProvider;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 银行卡四要素绑卡服务：KYC 通过为前置，姓名/证件由服务端注入；落 {@code cf_user_bank_card}，对外仅 {@code bindCardId}。
 */
@Service
public class BankCardBindingService {

    private static final Logger log = LoggerFactory.getLogger(BankCardBindingService.class);

    private final RoutingBankCardProvider router;
    private final UserBankCardMapper bankCardMapper;
    private final UserKycV2Mapper kycV2Mapper;
    private final BankCardProperties properties;

    public BankCardBindingService(RoutingBankCardProvider router,
                                  UserBankCardMapper bankCardMapper,
                                  UserKycV2Mapper kycV2Mapper,
                                  BankCardProperties properties) {
        this.router = router;
        this.bankCardMapper = bankCardMapper;
        this.kycV2Mapper = kycV2Mapper;
        this.properties = properties;
    }

    @Transactional
    public BankCardView bind(long userId, String cardNo, String bankCode, String reservedPhone, String idempotencyKey) {
        if (!StringUtils.hasText(cardNo) || !StringUtils.hasText(reservedPhone)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "卡号与预留手机号不能为空");
        }
        UserKycV2 kyc = requireKycPassed(userId);
        String fingerprint = IdCardFingerprintCalculator.hmacSha256Hex(
                properties.getFingerprintSalt(), kyc.getRealName(), cardNo);
        UserBankCard exists = bankCardMapper.selectOne(new LambdaQueryWrapper<UserBankCard>()
                .eq(UserBankCard::getUserId, userId)
                .eq(UserBankCard::getCardNoFingerprint, fingerprint));
        if (exists != null && "VERIFIED".equals(exists.getStatus())) {
            return toView(exists);
        }

        BankCardFourElementsProvider provider = router.resolve();
        BankCardVerifyResult result = provider.verify(new BankCardVerifyCommand(
                userId, kyc.getRealName(), kyc.getIdCardNo(), cardNo, bankCode, reservedPhone));
        if (!result.success()) {
            log.info("[bankcard] verify failed userId={} providerId={} code={}",
                    userId, provider.providerId(), result.internalFailureCode());
            persistFailed(userId, exists, cardNo, bankCode, reservedPhone, fingerprint,
                    provider.providerId(), result.internalFailureCode());
            throw new BusinessException(ErrorCode.KYC_BANKCARD_VERIFY_FAILED,
                    ErrorCode.KYC_BANKCARD_VERIFY_FAILED.getMessage());
        }

        Date now = new Date();
        UserBankCard row = exists != null ? exists : new UserBankCard();
        row.setUserId(userId);
        row.setBindCardId(row.getBindCardId() == null ? newBindCardId() : row.getBindCardId());
        row.setBankCode(bankCode);
        row.setCardNo(cardNo);
        row.setCardNoMask(MaskingUtil.maskBankCard(cardNo));
        row.setReservedPhone(reservedPhone);
        row.setReservedPhoneMask(MaskingUtil.maskPhone11(reservedPhone));
        row.setCardNoFingerprint(fingerprint);
        row.setStatus("VERIFIED");
        row.setIsPrimary(Boolean.FALSE);
        row.setProviderId(provider.providerId());
        row.setProviderTxnNo(result.providerTxnNo());
        row.setVerifiedAt(now);
        row.setUpdatedAt(now);
        if (row.getId() == null) {
            row.setCreatedAt(now);
            bankCardMapper.insert(row);
        } else {
            bankCardMapper.updateById(row);
        }
        markAsPrimary(userId, row);
        return toView(row);
    }

    @Transactional
    public List<BankCardView> list(long userId) {
        List<UserBankCard> rows = bankCardMapper.selectList(new LambdaQueryWrapper<UserBankCard>()
                .eq(UserBankCard::getUserId, userId)
                .orderByDesc(UserBankCard::getIsPrimary)
                .orderByDesc(UserBankCard::getVerifiedAt));
        List<BankCardView> views = new ArrayList<>(rows.size());
        for (UserBankCard r : rows) {
            views.add(toView(r));
        }
        return views;
    }

    @Transactional
    public BankCardView setPrimary(long userId, String bindCardId) {
        UserBankCard target = requireOwnedCard(userId, bindCardId);
        if (!"VERIFIED".equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.KYC_BANKCARD_VERIFY_FAILED, "卡片未通过四要素鉴权");
        }
        markAsPrimary(userId, target);
        return toView(target);
    }

    @Transactional
    public void unbind(long userId, String bindCardId) {
        UserBankCard target = requireOwnedCard(userId, bindCardId);
        target.setStatus("UNBOUND");
        target.setIsPrimary(Boolean.FALSE);
        target.setUnboundAt(new Date());
        target.setUpdatedAt(new Date());
        bankCardMapper.updateById(target);
    }

    private void markAsPrimary(long userId, UserBankCard target) {
        bankCardMapper.update(null, new LambdaUpdateWrapper<UserBankCard>()
                .eq(UserBankCard::getUserId, userId)
                .ne(UserBankCard::getId, target.getId())
                .eq(UserBankCard::getIsPrimary, Boolean.TRUE)
                .set(UserBankCard::getIsPrimary, Boolean.FALSE)
                .set(UserBankCard::getUpdatedAt, new Date()));
        target.setIsPrimary(Boolean.TRUE);
        target.setUpdatedAt(new Date());
        bankCardMapper.updateById(target);
    }

    private void persistFailed(long userId, UserBankCard existing, String cardNo, String bankCode,
                               String reservedPhone, String fingerprint,
                               String providerId, String failureCode) {
        Date now = new Date();
        UserBankCard row = existing != null ? existing : new UserBankCard();
        row.setUserId(userId);
        row.setBindCardId(row.getBindCardId() == null ? newBindCardId() : row.getBindCardId());
        row.setBankCode(bankCode);
        row.setCardNo(cardNo);
        row.setCardNoMask(MaskingUtil.maskBankCard(cardNo));
        row.setReservedPhone(reservedPhone);
        row.setReservedPhoneMask(MaskingUtil.maskPhone11(reservedPhone));
        row.setCardNoFingerprint(fingerprint);
        row.setStatus("FAILED");
        row.setIsPrimary(Boolean.FALSE);
        row.setProviderId(providerId);
        row.setProviderTxnNo(failureCode);
        row.setUpdatedAt(now);
        if (row.getId() == null) {
            row.setCreatedAt(now);
            bankCardMapper.insert(row);
        } else {
            bankCardMapper.updateById(row);
        }
    }

    private UserKycV2 requireKycPassed(long userId) {
        UserKycV2 kyc = kycV2Mapper.selectOne(new LambdaQueryWrapper<UserKycV2>()
                .eq(UserKycV2::getUserId, userId));
        if (kyc == null || !Boolean.TRUE.equals(kyc.getKycPassed())) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED,
                    ErrorCode.KYC_FACE_NOT_VERIFIED.getMessage());
        }
        if (!StringUtils.hasText(kyc.getRealName()) || !StringUtils.hasText(kyc.getIdCardNo())) {
            throw new BusinessException(ErrorCode.KYC_FACE_NOT_VERIFIED,
                    ErrorCode.KYC_FACE_NOT_VERIFIED.getMessage());
        }
        return kyc;
    }

    private UserBankCard requireOwnedCard(long userId, String bindCardId) {
        if (!StringUtils.hasText(bindCardId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "bindCardId required");
        }
        UserBankCard row = bankCardMapper.selectOne(new LambdaQueryWrapper<UserBankCard>()
                .eq(UserBankCard::getBindCardId, bindCardId));
        if (row == null || row.getUserId() == null || row.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该卡片");
        }
        return row;
    }

    private static String newBindCardId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static BankCardView toView(UserBankCard r) {
        return new BankCardView(
                r.getBindCardId(),
                r.getBankCode(),
                r.getCardNoMask(),
                r.getReservedPhoneMask(),
                r.getStatus(),
                Boolean.TRUE.equals(r.getIsPrimary()));
    }
}
