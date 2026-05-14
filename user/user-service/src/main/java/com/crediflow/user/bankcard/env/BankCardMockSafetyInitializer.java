package com.crediflow.user.bankcard.env;

import com.crediflow.user.bankcard.config.BankCardProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生产 profile + {@code kyc.bankcard.provider.mock=true}（或 active=mock）MUST 中止启动。
 */
@Component
public class BankCardMockSafetyInitializer {

    private final BankCardProperties properties;
    private final Environment environment;

    public BankCardMockSafetyInitializer(BankCardProperties properties, Environment environment) {
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
        if (properties.getProvider().isMock()) {
            throw new IllegalStateException(
                    "[BankCardMockSafety] 生产 profile 检测到 kyc.bankcard.provider.mock=true，启动中止");
        }
        if ("mock".equalsIgnoreCase(properties.getProvider().getActive())) {
            throw new IllegalStateException(
                    "[BankCardMockSafety] 生产 profile 检测到 kyc.bankcard.provider.active=mock，启动中止");
        }
    }
}
