package com.crediflow.user.face.provider;

import com.crediflow.user.face.config.FaceVerifyProperties;
import com.crediflow.user.face.model.CallbackParseResult;
import com.crediflow.user.face.model.FaceSubmitReceipt;
import com.crediflow.user.face.model.FaceVerifyCommand;
import com.crediflow.user.face.spi.FaceVerifyProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按 Nacos {@code kyc.face.provider.active} 委派到具体 Provider。
 */
@Component
@Primary
public class RoutingFaceVerifyProvider implements FaceVerifyProvider {

    private final FaceVerifyProperties properties;
    private final List<FaceVerifyProvider> providers;

    public RoutingFaceVerifyProvider(FaceVerifyProperties properties,
                                     List<FaceVerifyProvider> providers) {
        this.properties = properties;
        this.providers = providers;
    }

    @Override
    public FaceSubmitReceipt submit(FaceVerifyCommand command) {
        return resolve().submit(command);
    }

    @Override
    public CallbackParseResult parseCallback(byte[] rawBody, Map<String, String> headers) {
        return resolve().parseCallback(rawBody, headers);
    }

    @Override
    public boolean verifySignature(byte[] rawBody, Map<String, String> headers) {
        return resolve().verifySignature(rawBody, headers);
    }

    @Override
    public String providerId() {
        return resolve().providerId();
    }

    public FaceVerifyProvider resolve() {
        String active = properties.getProvider().getActive();
        for (FaceVerifyProvider p : providers) {
            if (p == this) {
                continue;
            }
            if (p.providerId().equalsIgnoreCase(active)) {
                return p;
            }
        }
        for (FaceVerifyProvider p : providers) {
            if (p == this) {
                continue;
            }
            if (MockFaceVerifyProvider.ID.equals(p.providerId())) {
                return p;
            }
        }
        throw new IllegalStateException("no face verify provider available, active=" + active);
    }
}
