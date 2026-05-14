package com.crediflow.user.realname.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "crediflow.realname")
public class RealnameProperties {

    /** 关闭后 step2 在非 Mock 下直接报配置错误（除 Mock 路径外） */
    private boolean enabled = true;
    private boolean mockSuccess = false;
    /** 选择签名策略 Bean 名称，如 noop、hmacSha256、groovyScript */
    private String providerType = "noop";
    private String baseUrl = "";
    private String appKey = "";
    private String appSecret = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);
    /** JSON 请求体模板，占位符：{{realName}} {{idCardNo}} {{timestamp}} {{appKey}} {{signature}} */
    private String requestBodyTemplate = "{\"name\":\"{{realName}}\",\"idNo\":\"{{idCardNo}}\",\"ts\":{{timestamp}}}";
    /** 生产 profile 判定：激活 profile 名（小写比较）命中即视为生产 */
    private java.util.List<String> prodProfiles = java.util.List.of("prod", "production");
    /** 证件幂等盐（应来自环境/Nacos，勿提交生产值） */
    private String idempotencySalt = "change-me-in-nacos";
    private int rateLimitWindowSeconds = 3600;
    private int rateLimitMaxRequests = 20;
    private int idempotencyTtlSeconds = 300;
    /** Groovy 脚本（返回 String 签名）；为空且策略为 groovyScript 时回退 noop */
    private String signatureScript = "";
    /** 响应 JSON 相对根路径：是否匹配 */
    private String responseMatchedJsonPath = "match";
    private String responseTxnNoJsonPath = "txnNo";
    private String responseMessageJsonPath = "message";
    private String responseIdValidJsonPath = "idValid";
    /** HTTP 头追加（如鉴权），值支持 {{appKey}} */
    private Map<String, String> extraHeaders = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMockSuccess() {
        return mockSuccess;
    }

    public void setMockSuccess(boolean mockSuccess) {
        this.mockSuccess = mockSuccess;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getRequestBodyTemplate() {
        return requestBodyTemplate;
    }

    public void setRequestBodyTemplate(String requestBodyTemplate) {
        this.requestBodyTemplate = requestBodyTemplate;
    }

    public java.util.List<String> getProdProfiles() {
        return prodProfiles;
    }

    public void setProdProfiles(java.util.List<String> prodProfiles) {
        this.prodProfiles = prodProfiles;
    }

    public String getIdempotencySalt() {
        return idempotencySalt;
    }

    public void setIdempotencySalt(String idempotencySalt) {
        this.idempotencySalt = idempotencySalt;
    }

    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public int getRateLimitMaxRequests() {
        return rateLimitMaxRequests;
    }

    public void setRateLimitMaxRequests(int rateLimitMaxRequests) {
        this.rateLimitMaxRequests = rateLimitMaxRequests;
    }

    public int getIdempotencyTtlSeconds() {
        return idempotencyTtlSeconds;
    }

    public void setIdempotencyTtlSeconds(int idempotencyTtlSeconds) {
        this.idempotencyTtlSeconds = idempotencyTtlSeconds;
    }

    public String getSignatureScript() {
        return signatureScript;
    }

    public void setSignatureScript(String signatureScript) {
        this.signatureScript = signatureScript;
    }

    public String getResponseMatchedJsonPath() {
        return responseMatchedJsonPath;
    }

    public void setResponseMatchedJsonPath(String responseMatchedJsonPath) {
        this.responseMatchedJsonPath = responseMatchedJsonPath;
    }

    public String getResponseTxnNoJsonPath() {
        return responseTxnNoJsonPath;
    }

    public void setResponseTxnNoJsonPath(String responseTxnNoJsonPath) {
        this.responseTxnNoJsonPath = responseTxnNoJsonPath;
    }

    public String getResponseMessageJsonPath() {
        return responseMessageJsonPath;
    }

    public void setResponseMessageJsonPath(String responseMessageJsonPath) {
        this.responseMessageJsonPath = responseMessageJsonPath;
    }

    public String getResponseIdValidJsonPath() {
        return responseIdValidJsonPath;
    }

    public void setResponseIdValidJsonPath(String responseIdValidJsonPath) {
        this.responseIdValidJsonPath = responseIdValidJsonPath;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }

    public void setExtraHeaders(Map<String, String> extraHeaders) {
        this.extraHeaders = extraHeaders != null ? extraHeaders : new HashMap<>();
    }
}
