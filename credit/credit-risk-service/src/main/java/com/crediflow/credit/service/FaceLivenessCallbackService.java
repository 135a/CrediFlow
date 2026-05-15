package com.crediflow.credit.service;

import com.crediflow.credit.dto.FaceCallbackRequest;

/**
 * 二次人脸活体回调后的申请状态与后续动作（额度、人工审核等），与 HTTP 层解耦。
 */
public interface FaceLivenessCallbackService {

    /**
     * 处理人脸验证回调：校验申请状态、按风险档更新状态并触发额度或人工审核流程。
     */
    void handleCallback(FaceCallbackRequest request);
}
