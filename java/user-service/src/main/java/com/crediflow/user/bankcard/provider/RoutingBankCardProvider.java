package com.crediflow.user.bankcard.provider;

import com.crediflow.user.bankcard.config.BankCardProperties;
import com.crediflow.user.bankcard.model.BankCardVerifyCommand;
import com.crediflow.user.bankcard.model.BankCardVerifyResult;
import com.crediflow.user.bankcard.spi.BankCardFourElementsProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class RoutingBankCardProvider implements BankCardFourElementsProvider {

    private final BankCardProperties properties;
    private final List<BankCardFourElementsProvider> providers;

    public RoutingBankCardProvider(BankCardProperties properties,
                                   List<BankCardFourElementsProvider> providers) {
        this.properties = properties;
        this.providers = providers;
    }

    @Override
    public BankCardVerifyResult verify(BankCardVerifyCommand command) {
        return resolve().verify(command);
    }

    @Override
    public String providerId() {
        return resolve().providerId();
    }

    public BankCardFourElementsProvider resolve() {
        String active = properties.getProvider().isMock() ? MockBankCardProvider.ID
                : properties.getProvider().getActive();
        for (BankCardFourElementsProvider p : providers) {
            if (p == this) {
                continue;
            }
            if (p.providerId().equalsIgnoreCase(active)) {
                return p;
            }
        }
        for (BankCardFourElementsProvider p : providers) {
            if (p == this) {
                continue;
            }
            if (MockBankCardProvider.ID.equals(p.providerId())) {
                return p;
            }
        }
        throw new IllegalStateException("no bankcard provider available, active=" + active);
    }
}
