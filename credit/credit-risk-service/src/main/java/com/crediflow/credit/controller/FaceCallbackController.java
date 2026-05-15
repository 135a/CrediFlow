package com.crediflow.credit.controller;

import com.crediflow.common.web.Result;
import com.crediflow.credit.dto.FaceCallbackRequest;
import com.crediflow.credit.service.FaceLivenessCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 人脸验证回调控制器：仅负责接入 HTTP，具体业务见 {@link FaceLivenessCallbackService}。
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/credit/face")
public class
FaceCallbackController {

    @Autowired
    private FaceLivenessCallbackService faceLivenessCallbackService;

    /**
     * 处理人脸验证回调
     *
     * @param request 包含申请ID、用户ID、验证结果和错误信息
     */
    @PostMapping("/callback")
    public Result<Void> handleFaceCallback(@RequestBody FaceCallbackRequest request) {
        log.info("Received face liveness callback for applicationId: {}, passed: {}",
                request.getApplicationId(), request.isPassed());
        faceLivenessCallbackService.handleCallback(request);
        return Result.success();
    }
}
