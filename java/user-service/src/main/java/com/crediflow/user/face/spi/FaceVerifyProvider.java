package com.crediflow.user.face.spi;

import com.crediflow.user.face.model.CallbackParseResult;
import com.crediflow.user.face.model.FaceSubmitReceipt;
import com.crediflow.user.face.model.FaceVerifyCommand;

import java.util.Map;

/**
 * 人脸实人核验第三方抽象。
 * <p>实现类由 Nacos 通过 {@code kyc.face.provider.active} 动态选择；本接口为顶层签名，
 * 不绑死任何具体厂商。{@code submit} 仅完成同步受理，终态由 {@link #parseCallback} 驱动。</p>
 */
public interface FaceVerifyProvider {

    FaceSubmitReceipt submit(FaceVerifyCommand command);

    CallbackParseResult parseCallback(byte[] rawBody, Map<String, String> headers);

    boolean verifySignature(byte[] rawBody, Map<String, String> headers);

    String providerId();
}
