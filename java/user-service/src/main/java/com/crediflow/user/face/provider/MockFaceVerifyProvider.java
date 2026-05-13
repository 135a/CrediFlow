package com.crediflow.user.face.provider;

import com.crediflow.user.face.model.CallbackParseResult;
import com.crediflow.user.face.model.FaceSubmitReceipt;
import com.crediflow.user.face.model.FaceVerifyCommand;
import com.crediflow.user.face.spi.FaceVerifyProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 非生产 Mock 实现：不外呼、不调摄像头，立即受理成功。
 */
@Component
public class MockFaceVerifyProvider implements FaceVerifyProvider {

    public static final String ID = "mock";

    @Override
    public FaceSubmitReceipt submit(FaceVerifyCommand command) {
        return new FaceSubmitReceipt(true, command.providerBizNo(), "MOCK-" + command.providerBizNo());
    }

    @Override
    public CallbackParseResult parseCallback(byte[] rawBody, Map<String, String> headers) {
        return new CallbackParseResult(null, null, "SUCCESS", null, Collections.emptyMap());
    }

    @Override
    public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
        return true;
    }

    @Override
    public String providerId() {
        return ID;
    }
}
