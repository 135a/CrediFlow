package com.crediflow.user.kyc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.api.user.UserEligibilityResponse;
import com.crediflow.common.web.Result;
import com.crediflow.user.bankcard.entity.UserBankCard;
import com.crediflow.user.bankcard.mapper.UserBankCardMapper;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部反查：仅返回 kycPassed / hasPrimaryBankCard，不暴露明文 PII。
 */
@RestController
@RequestMapping("/api/internal/user")
public class InternalUserEligibilityController {

    private final UserKycV2Mapper kycV2Mapper;
    private final UserBankCardMapper bankCardMapper;

    public InternalUserEligibilityController(UserKycV2Mapper kycV2Mapper,
                                             UserBankCardMapper bankCardMapper) {
        this.kycV2Mapper = kycV2Mapper;
        this.bankCardMapper = bankCardMapper;
    }

    @GetMapping("/eligibility")
    public Result<UserEligibilityResponse> eligibility(@RequestParam("userId") Long userId) {
        boolean kycPassed = false;
        if (userId != null) {
            UserKycV2 kyc = kycV2Mapper.selectOne(new LambdaQueryWrapper<UserKycV2>()
                    .eq(UserKycV2::getUserId, userId));
            kycPassed = kyc != null && Boolean.TRUE.equals(kyc.getKycPassed());
        }
        boolean hasPrimary = false;
        if (userId != null) {
            Long count = bankCardMapper.selectCount(new LambdaQueryWrapper<UserBankCard>()
                    .eq(UserBankCard::getUserId, userId)
                    .eq(UserBankCard::getStatus, "VERIFIED")
                    .eq(UserBankCard::getIsPrimary, Boolean.TRUE));
            hasPrimary = count != null && count > 0;
        }
        return Result.success(new UserEligibilityResponse(kycPassed, hasPrimary));
    }
}
