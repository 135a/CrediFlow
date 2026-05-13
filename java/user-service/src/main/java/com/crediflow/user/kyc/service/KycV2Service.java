package com.crediflow.user.kyc.service;

import com.crediflow.user.kyc.dto.KycStep1Response;

/**
 * KYC v2 主流程入口。step2（人脸）与 status 查询将于 Phase 2/3 补齐。
 */
public interface KycV2Service {

    /**
     * 二要素 + 准入闸门一体化提交（OpenSpec：user-kyc-authentication / kyc-eligibility-gate）。
     *
     * @param userId         当前登录用户
     * @param realName       姓名
     * @param idCardNo       18 位身份证号
     * @param idempotencyKey 客户端可选幂等键
     */
    KycStep1Response submitStep1(long userId, String realName, String idCardNo, String idempotencyKey);
}
