package com.crediflow.user.eligibility.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.user.kyc.entity.UserKycV2;
import com.crediflow.user.kyc.mapper.UserKycV2Mapper;
import org.springframework.stereotype.Component;

/**
 * 一人一证一账号：以 {@code id_card_fingerprint} 唯一索引拦截；同 userId 视为幂等。
 */
@Component
public class IdCardUniquenessPolicy {

    private final UserKycV2Mapper mapper;

    public IdCardUniquenessPolicy(UserKycV2Mapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @return true 表示通过（无冲突或冲突属于同一 userId）
     */
    public boolean isUnique(long userId, String idCardFingerprint) {
        if (idCardFingerprint == null || idCardFingerprint.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<UserKycV2> qw = new LambdaQueryWrapper<UserKycV2>()
                .eq(UserKycV2::getIdCardFingerprint, idCardFingerprint);
        UserKycV2 existing = mapper.selectOne(qw);
        return existing == null || existing.getUserId() != null && existing.getUserId() == userId;
    }
}
