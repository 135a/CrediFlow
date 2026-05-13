package com.crediflow.user.face.env;

import com.crediflow.user.face.config.FaceVerifyProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动期强断言：生产 profile 下 {@code kyc.face.verify.mock=true}（或 active=mock）MUST 中止启动。
 */
@Component
public class FaceMockSafetyInitializer {

    private final FaceVerifyProperties properties;
    private final Environment environment;

    public FaceMockSafetyInitializer(FaceVerifyProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void assertSafe() {
        Set<String> active = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<String> prod = properties.getProdProfiles().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        if (active.stream().noneMatch(prod::contains)) {
            return;
        }
        if (properties.getVerify().isMock()) {
            throw new IllegalStateException(
                    "[FaceMockSafety] 生产 profile 检测到 kyc.face.verify.mock=true，启动中止");
        }
        if ("mock".equalsIgnoreCase(properties.getProvider().getActive())) {
            throw new IllegalStateException(
                    "[FaceMockSafety] 生产 profile 检测到 kyc.face.provider.active=mock，启动中止");
        }
    }
}
