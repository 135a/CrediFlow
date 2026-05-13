package com.crediflow.user.bankcard.provider;

import com.crediflow.user.bankcard.config.BankCardProperties;
import com.crediflow.user.bankcard.model.BankCardVerifyCommand;
import com.crediflow.user.bankcard.model.BankCardVerifyResult;
import com.crediflow.user.bankcard.spi.BankCardFourElementsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 四要素 HTTP Provider 占位骨架：未配置 baseUrl 时返回失败终态（FAIL_CFG），
 * 真实厂商接入留独立 change。
 */
@Component
public class HttpBankCardProvider implements BankCardFourElementsProvider {

    public static final String ID = "http";

    private static final Logger log = LoggerFactory.getLogger(HttpBankCardProvider.class);

    private final BankCardProperties properties;

    public HttpBankCardProvider(BankCardProperties properties) {
        this.properties = properties;
    }

    @Override
    public BankCardVerifyResult verify(BankCardVerifyCommand command) {
        String active = properties.getProvider().getActive();
        BankCardProperties.ProviderConfig cfg = properties.getProviders().get(active);
        if (cfg == null || cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            log.warn("[bankcard] http provider not configured active={} userId={}", active, command.userId());
            return new BankCardVerifyResult(false, null, "FAIL_CFG_BASE_URL");
        }
        // TODO: 实现 applyTemplate -> sign -> POST -> parse 真实厂商协议。
        log.warn("[bankcard] http verify stub invoked, returning FAIL_NOT_IMPLEMENTED");
        return new BankCardVerifyResult(false, null, "FAIL_NOT_IMPLEMENTED");
    }

    @Override
    public String providerId() {
        return ID;
    }
}
