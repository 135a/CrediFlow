## MODIFIED Requirements

### Requirement: 底层核心微服务暴露路径内网强制隔离

所有的底层业务核心微服务（如 `loan-contract-service` 及 `credit-risk-service` 等）对外提供的内网接口 MUST 严格配置为 `/api/internal/` 路径前缀，且这必须体现为 Controller 类级别的 `@RequestMapping` 隔离。底层微服务 MUST NOT 将内部通信接口挂载在形如 `/api/app/` 或 `/api/admin/` 等暴露公网的前缀下。外部流量通过 APISIX 网关进入后，MUST 先由对应的 BFF（如 `app-bff-service`）承接，再由 BFF 使用 Feign 追加内网 HMAC 签名（`X-Internal-Sign`）后转发至底层微服务 `/api/internal/` 的隔离端点。

#### Scenario: 外部直接访问底层微服务接口

- **WHEN** 客户端或攻击者试图通过公网直接请求底层服务的暴露接口（如额度扣减等敏感操作）
- **THEN** 基础架构与类级路径规范 MUST 确保底层服务相应的 Controller 被严格限制在 `/api/internal/`，从而直接在网关/内部拦截器层因不符合规范或缺失有效签名而被拦截，MUST 返回 404 Not Found 或 401 Unauthorized。
