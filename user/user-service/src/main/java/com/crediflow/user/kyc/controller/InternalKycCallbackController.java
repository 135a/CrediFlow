package com.crediflow.user.kyc.controller;

import com.crediflow.common.web.Result;
import com.crediflow.user.face.service.FaceCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 人脸厂商异步回调入口。
 * <p>不挂 {@code @Inner}：厂商不持有内网密钥；签名由 {@link FaceCallbackService}
 * 调用 {@code FaceVerifyProvider.verifySignature} 完成。
 * 内网层防护由 APISIX / IP 白名单 / {@code internal-api-security} 在边缘完成。</p>
 */
@RestController
@RequestMapping("/api/internal/face-verify")
public class InternalKycCallbackController {

    private static final Logger log = LoggerFactory.getLogger(InternalKycCallbackController.class);

    private final FaceCallbackService callbackService;

    public InternalKycCallbackController(FaceCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/callback")
    public ResponseEntity<Result<String>> faceCallback(HttpServletRequest request) {
        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.warn("[face-callback] read body failed: {}", e.toString());
            return ResponseEntity.status(400).body(Result.error(400, "INVALID_BODY", null));
        }
        Map<String, String> headers = collectHeaders(request);
        FaceCallbackService.Outcome outcome = callbackService.handle(body, headers);
        switch (outcome) {
            case SIGNATURE_INVALID:
                return ResponseEntity.status(401).body(Result.error(401, "SIGNATURE_INVALID", null));
            case ORPHAN:
                return ResponseEntity.status(404).body(Result.error(404, "BIZ_NOT_FOUND", null));
            case IDEMPOTENT_HIT:
            case OK:
            default:
                return ResponseEntity.ok(Result.success("ACK"));
        }
    }

    private Map<String, String> collectHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        var names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            map.put(n, request.getHeader(n));
        }
        return map;
    }
}
