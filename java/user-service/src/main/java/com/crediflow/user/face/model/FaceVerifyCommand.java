package com.crediflow.user.face.model;

/**
 * 人脸核验受理命令（厂商端到端一次调用）。
 */
public record FaceVerifyCommand(
        long userId,
        String activeFaceToken,
        String providerBizNo,
        String idempotencyKey) {}
