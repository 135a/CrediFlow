package com.crediflow.user.face.model;

/**
 * 人脸核验同步受理回执。终态由厂商异步回调驱动；upstreamTxnNo 在受理阶段可能为空。
 */
public record FaceSubmitReceipt(
        boolean accepted,
        String providerBizNo,
        String upstreamTxnNo) {}
