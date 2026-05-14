package com.crediflow.user.face.provider;

import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.user.face.config.FaceVerifyProperties;
import com.crediflow.user.face.model.CallbackParseResult;
import com.crediflow.user.face.model.FaceSubmitReceipt;
import com.crediflow.user.face.model.FaceVerifyCommand;
import com.crediflow.user.face.spi.FaceVerifyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * HTTP 模板化人脸 Provider 骨架。
 *
 * <p>本骨架完成了配置读取、签名策略选择槽位（与 RealnameSignatureStrategySelector 同套路），
 * 但具体厂商 baseUrl / 报文协议留到独立 change {@code kyc-face-provider-&lt;vendor&gt;} 接入。
 * 在未配置具体 providerId 时 MUST 返回 retryable 错误，禁止以"成功"占位放行。</p>
 */
@Component
public class HttpFaceVerifyProvider implements FaceVerifyProvider {

    public static final String ID = "http";

    private static final Logger log = LoggerFactory.getLogger(HttpFaceVerifyProvider.class);

    private final FaceVerifyProperties properties;

    public HttpFaceVerifyProvider(FaceVerifyProperties properties) {
        this.properties = properties;
    }

    @Override
    public FaceSubmitReceipt submit(FaceVerifyCommand command) {
        String active = properties.getProvider().getActive();
        FaceVerifyProperties.ProviderConfig cfg = properties.getProviders().get(active);
        if (cfg == null || cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            log.warn("[face] http provider not configured active={} userId={}", active, command.userId());
            throw new BusinessException(ErrorCode.KYC_FACE_RETRYABLE,
                    ErrorCode.KYC_FACE_RETRYABLE.getMessage());
        }
        log.info("[face] http submit placeholder active={} userId={} bizNo={}",
                active, command.userId(), command.providerBizNo());
        // TODO: 接入真实厂商时实现：applyTemplate -> sign -> POST -> parse；
        //       目前仅返回受理态，终态依赖异步回调。
        return new FaceSubmitReceipt(true, command.providerBizNo(), null);
    }

    @Override
    public CallbackParseResult parseCallback(byte[] rawBody, Map<String, String> headers) {
        // 骨架解析：终态默认 SUCCESS，由具体厂商实现覆盖。
        return new CallbackParseResult(null, null, "SUCCESS", null, Collections.emptyMap());
    }

    @Override
    public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
        // 骨架：默认拒绝，必须由厂商实现覆盖签名规则；防止意外通过验签。
        log.warn("[face] http verifySignature called on stub provider, returning false");
        return false;
    }

    @Override
    public String providerId() {
        return ID;
    }
}
