package com.crediflow.user.kyc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.auth.annotation.Inner;
import com.crediflow.common.web.Result;
import com.crediflow.user.bankcard.entity.UserBankCard;
import com.crediflow.user.bankcard.mapper.UserBankCardMapper;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部反查接口：授信 / 借款受理前置使用。
 * <p>仅返回 {@code kycPassed} 与 {@code hasPrimaryBankCard}，
 * MUST NOT 暴露明文姓名 / 身份证 / 卡号 / 失败原因。</p>
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

    @Inner
    @GetMapping("/eligibility")
    public Result<Map<String, Object>> eligibility(@RequestParam("userId") Long userId) {
        Map<String, Object> resp = new HashMap<>();
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
        resp.put("kycPassed", kycPassed);
        resp.put("hasPrimaryBankCard", hasPrimary);
        return Result.success(resp);
    }
}
