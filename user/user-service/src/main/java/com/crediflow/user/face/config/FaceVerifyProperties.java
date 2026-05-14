package com.crediflow.user.face.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人脸实人核验配置（Nacos）：
 *
 * <pre>
 * kyc.face.verify.mock=true               # 全局跳过厂商 SDK / 外呼；仅非生产可用
 * kyc.face.verify.whitelist.phones=[]     # 手机号白名单
 * kyc.face.verify.whitelist.fingerprints=[]  # 身份证指纹白名单
 * kyc.face.provider.active=mock           # mock / &lt;providerId&gt;
 * kyc.face.idempotency-ttl-seconds=86400
 * kyc.face.processing-ttl-seconds=1800
 * kyc.face.prod-profiles=[prod,production]
 * kyc.face.providers.tencent.base-url=https://...
 * </pre>
 */
@ConfigurationProperties(prefix = "kyc.face")
public class FaceVerifyProperties {

    private Verify verify = new Verify();
    private ProviderRouting provider = new ProviderRouting();
    private int idempotencyTtlSeconds = 86400;
    private int processingTtlSeconds = 1800;
    private List<String> prodProfiles = List.of("prod", "production");
    private Map<String, ProviderConfig> providers = new HashMap<>();

    public Verify getVerify() { return verify; }
    public void setVerify(Verify verify) { this.verify = verify == null ? new Verify() : verify; }

    public ProviderRouting getProvider() { return provider; }
    public void setProvider(ProviderRouting provider) { this.provider = provider == null ? new ProviderRouting() : provider; }

    public int getIdempotencyTtlSeconds() { return idempotencyTtlSeconds; }
    public void setIdempotencyTtlSeconds(int v) { this.idempotencyTtlSeconds = v; }

    public int getProcessingTtlSeconds() { return processingTtlSeconds; }
    public void setProcessingTtlSeconds(int v) { this.processingTtlSeconds = v; }

    public List<String> getProdProfiles() { return prodProfiles; }
    public void setProdProfiles(List<String> v) { this.prodProfiles = v == null ? List.of() : v; }

    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> v) { this.providers = v == null ? new HashMap<>() : v; }

    public static class Verify {
        private boolean mock = false;
        private Whitelist whitelist = new Whitelist();

        public boolean isMock() { return mock; }
        public void setMock(boolean mock) { this.mock = mock; }
        public Whitelist getWhitelist() { return whitelist; }
        public void setWhitelist(Whitelist v) { this.whitelist = v == null ? new Whitelist() : v; }
    }

    public static class Whitelist {
        private List<String> phones = new ArrayList<>();
        private List<String> fingerprints = new ArrayList<>();

        public List<String> getPhones() { return phones; }
        public void setPhones(List<String> v) { this.phones = v == null ? new ArrayList<>() : v; }
        public List<String> getFingerprints() { return fingerprints; }
        public void setFingerprints(List<String> v) { this.fingerprints = v == null ? new ArrayList<>() : v; }
    }

    public static class ProviderRouting {
        private String active = "mock";

        public String getActive() { return active; }
        public void setActive(String active) { this.active = active == null ? "mock" : active; }
    }

    public static class ProviderConfig {
        private String baseUrl = "";
        private String appKey = "";
        private String appSecret = "";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(10);
        private String requestTemplate = "{}";
        /** noop / hmacSha256 / groovyScript */
        private String signStrategy = "noop";
        private String signatureScript = "";
        private Map<String, String> extraHeaders = new HashMap<>();

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
        public String getRequestTemplate() { return requestTemplate; }
        public void setRequestTemplate(String v) { this.requestTemplate = v; }
        public String getSignStrategy() { return signStrategy; }
        public void setSignStrategy(String v) { this.signStrategy = v; }
        public String getSignatureScript() { return signatureScript; }
        public void setSignatureScript(String v) { this.signatureScript = v; }
        public Map<String, String> getExtraHeaders() { return extraHeaders; }
        public void setExtraHeaders(Map<String, String> v) { this.extraHeaders = v == null ? new HashMap<>() : v; }
    }
}
