## Purpose

TBD

## Requirements

### Requirement: App 端 BFF 边界

`app-bff-service` MUST 仅暴露面向 **终端用户（C 端）** 的 HTTP API；对外路径前缀 MUST 为 **`/api/app/**`**（或由网关剥离前缀后一致）；MUST NOT 暴露仅管理端可用的写操作或敏感运营接口。

#### Scenario: 未授权管理操作不可达

- **WHEN** 调用方仅持有终端用户 JWT 并尝试访问管理端专属路由（若误挂载在 App BFF）
- **THEN** App BFF MUST 返回 403 或 MUST 不提供该路由注册

### Requirement: 与领域服务协作

App BFF MUST 通过 OpenFeign/HTTP 调用领域微服务；MUST 透传 `X-Request-Id`；MUST 对下游错误做统一映射且不泄露内部栈信息。

#### Scenario: 下游超时

- **WHEN** 领域服务读超时
- **THEN** App BFF MUST 返回网关可对齐的错误码且 MUST 记录 request id
