package com.crediflow.user.bankcard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 银行卡四要素绑卡配置：
 *
 * <pre>
 * kyc.bankcard.provider.active=mock
 * kyc.bankcard.provider.mock=true     # 仅非生产可用
 * kyc.bankcard.fingerprint-salt=change-me-in-nacos
 * kyc.bankcard.providers.unionpay.base-url=https://...
 * </pre>
 */
@ConfigurationProperties(prefix = "kyc.bankcard")
public class BankCardProperties {

    private Provider provider = new Provider();
    private String fingerprintSalt = "change-me-in-nacos";
    private List<String> prodProfiles = List.of("prod", "production");
    private Map<String, ProviderConfig> providers = new HashMap<>();

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider == null ? new Provider() : provider; }
    public String getFingerprintSalt() { return fingerprintSalt; }
    public void setFingerprintSalt(String v) { this.fingerprintSalt = v; }
    public List<String> getProdProfiles() { return prodProfiles; }
    public void setProdProfiles(List<String> v) { this.prodProfiles = v == null ? List.of() : v; }
    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> v) { this.providers = v == null ? new HashMap<>() : v; }

    public static class Provider {
        private String active = "mock";
        /** 全局 mock：true 时所有绑卡四要素请求直通成功。仅非生产可用。 */
        private boolean mock = false;

        public String getActive() { return active; }
        public void setActive(String active) { this.active = active == null ? "mock" : active; }
        public boolean isMock() { return mock; }
        public void setMock(boolean mock) { this.mock = mock; }
    }

    public static class ProviderConfig {
        private String baseUrl = "";
        private String appKey = "";
        private String appSecret = "";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(10);
        private String signStrategy = "noop";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String v) { this.appKey = v; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String v) { this.appSecret = v; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration v) { this.connectTimeout = v; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration v) { this.readTimeout = v; }
        public String getSignStrategy() { return signStrategy; }
        public void setSignStrategy(String v) { this.signStrategy = v; }
    }
}
