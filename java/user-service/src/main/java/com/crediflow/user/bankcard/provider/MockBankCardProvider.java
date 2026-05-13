package com.crediflow.user.bankcard.provider;

import com.crediflow.user.bankcard.model.BankCardVerifyCommand;
import com.crediflow.user.bankcard.model.BankCardVerifyResult;
import com.crediflow.user.bankcard.spi.BankCardFourElementsProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 非生产 Mock：直接判定通过。生产 profile 由 {@link com.crediflow.user.bankcard.env.BankCardMockSafetyInitializer}
 * 在启动期阻断。
 */
@Component
public class MockBankCardProvider implements BankCardFourElementsProvider {

    public static final String ID = "mock";

    @Override
    public BankCardVerifyResult verify(BankCardVerifyCommand command) {
        return new BankCardVerifyResult(true,
                "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                null);
    }

    @Override
    public String providerId() {
        return ID;
    }
}
