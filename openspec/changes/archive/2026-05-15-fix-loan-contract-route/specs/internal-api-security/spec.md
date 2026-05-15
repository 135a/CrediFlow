## ADDED Requirements

### Requirement: 底层核心微服务暴露路径内网强制隔离
所有的底层业务核心微服务（如 `loan-contract-service` 的 `LoanContractController`）对外提供的接口 MUST 严格配置为 `/api/internal/` 路径前缀。底层微服务 MUST NOT 直接暴露形如 `/api/app/` 或 `/api/admin/` 的路径。外部流量通过 APISIX 网关进入后，MUST 先由对应的 BFF（如 `app-bff-service`）承接，再由 BFF 使用 Feign 追加内网 HMAC 签名（`X-Internal-Sign`）后转发至底层微服务。

#### Scenario: 外部直接访问底层微服务接口
- **WHEN** 客户端或攻击者试图通过公网直接请求 `loan-contract-service` 的 `/api/app/loan-contract/sign`
- **THEN** 基础架构与路径规范 MUST 确保底层服务不监听此对外路径，或直接在网关/内部拦截器层因不符合规范而被拦截，MUST 返回 404 Not Found 或 401 Unauthorized。
