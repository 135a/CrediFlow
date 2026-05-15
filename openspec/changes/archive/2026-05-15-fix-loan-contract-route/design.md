## Context

`loan-contract-service` 目前的 `LoanContractController` 使用了 `@RequestMapping("/api/app/loan-contract")` 作为路由前缀。根据 CrediFlow 既定的网关与零信任架构规范，只有 BFF 层才能直接暴露 `/api/app/` 或 `/api/admin/` 路径；底层的核心业务服务（如借款合同服务）必须通过 `/api/internal/` 提供接口，并受到 `InternalAuthFilter` 的内网 HMAC 安全签名强制校验。因此，目前的暴露路径不仅让公网请求形成死胡同（因为网关直接路由给了 BFF），而且严重违反了底层服务不直连外部的隔离原则。

## Goals / Non-Goals

**Goals:**
- 修改 `LoanContractController` 的基座路径，由 `/api/app/loan-contract` 更改为 `/api/internal/contract`。
- 确保 `loan-contract-service` 的 API 符合系统全局的安全控制规范。

**Non-Goals:**
- 本次变更暂不在 BFF 层（`app-bff-service`）中重新实现针对合同接口的代理逻辑。如果原前端业务需要签约或查询合同，应在后续由 BFF 团队创建聚合接口调用。本变更仅专注于消除底层架构的安全隐患。

## Decisions

- **决议 1: 将 `LoanContractController` 的 `RequestMapping` 修改为 `/api/internal/contract`**
  - **Rationale**: 切断该微服务直接暴露外部契约的可能性，强制其回归底层微服务定位。所有接口天然受到 `/api/internal/` 的保护，彻底修复架构错位。

## Risks / Trade-offs

- **Risk: 前端无法直接请求到合同服务**
  - **Mitigation**: 事实上，由于 APISIX 网关的规则，前端本来也无法直接穿透 `/api/app/` 达到底层服务（会被导向 BFF），所以此问题在生产上只是一个被隐藏的代码缺陷。收缩为 internal 是正确的修补手段。
