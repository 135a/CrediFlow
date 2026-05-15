## Why
在现有的架构规范中，`APISIX` 会将所有 `/api/app/**` 路径的流量直接路由到 `app-bff-service`。然而，`loan-contract-service` 作为底层的核心微服务，其 `LoanContractController` 却错误地配置了 `@RequestMapping("/api/app/loan-contract")` 路径。这不仅导致公网流量无法触达（因为网关分流机制），更违反了“后端底层微服务必须通过 `/api/internal/` 提供内网隔离调用”的安全设计原则。

## What Changes
- 修改 `LoanContractController` 的基础路径：由 `@RequestMapping("/api/app/loan-contract")` 更改为 `@RequestMapping("/api/internal/contract")`。
- 将相应的子路径前缀补齐或调整，使得所有合同查询和签署请求都置于内网安全管控之下。
- **BREAKING**: 原有试图暴露在外网的合同 API 发生路由变动，并被强制纳入内网 HMAC 签名校验。

## Capabilities

### New Capabilities

### Modified Capabilities
- `internal-api-security`: 将 `LoanContractController` 的接口路径正式迁入 `/api/internal/` 约束规范，确保其强制受 `InternalAuthFilter` 保护。

## Impact
- `loan-contract-service` 的请求暴露契约被修改。
- 保证了架构分层的一致性：外部流量进网关 -> 路由到 BFF -> BFF 通过 Feign (带内网签名) 调后端基础服务。
