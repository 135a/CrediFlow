# 提案：清理遗留 JWT 内网认证（对齐 HMAC 主链路）

## Why

`crediflow-common` 中同时存在基于 `X-Internal-Token`（JWT）的发送端拦截器与从未注册的接收端过滤器，与当前已落地且写入规范的 **HMAC + `X-Timestamp` + `X-Internal-Sign`** 内网主链路并存，造成同名类、过时文档与每次 Feign 调用的无效开销，并增加误用 `auth` 包下 `InternalAuthFilter` 导致安全回退的风险。应在不削弱既有 `internal-api-security` 行为的前提下，移除该遗留路径并与仓库事实对齐。

## What Changes

- 删除或下线未使用的 `com.crediflow.common.auth.InternalAuthFilter`（从未作为 Spring Bean 注册，且拦截路径为 `/internal/` 与真实 `/api/internal/**` 不一致）。
- 从全局 Feign 配置中移除 `InternalAuthInterceptor`（JWT 注入 `X-Internal-Token`），避免与主链路重复及潜在异常牵连。
- 清理 `JwtUtils`（或等价类）中仅服务于上述 JWT 内网路径的生成/校验方法，若确认无其他引用。
- 更新 `docs/internal-auth.md`：改为描述 **HMAC 内网签名校验**（与 `InternalAuthRequestInterceptor`、`filter.InternalAuthFilter`、OpenSpec `internal-api-security` 一致），删除或更正对 JWT 流程的叙述。
- 复核全仓库对 `X-Internal-Token`、`InternalAuthInterceptor`、`validateInternalToken` 等符号的引用，确保无外部契约依赖后再删。

## Capabilities

### New Capabilities

- 无：本变更不引入新的业务能力规范目录；仅收敛实现与文档。

### Modified Capabilities

- `internal-api-security`：在变更内的 **delta spec** 中增补一条**澄清性**要求——微服务间 `/api/internal/**` 的主校验 MUST 继续以 `X-Timestamp` + `X-Internal-Sign`（与现有 Scenario）为准；**不得**将基于 `X-Internal-Token` 的 JWT 作为该类接口的必需或并联主校验机制（与 README、归档变更及当前 `InternalAuthRequestInterceptor` / `InternalAuthFilter` 实现一致）。

## Impact

- **代码**：`crediflow-common`（`auth` 包过滤器与拦截器、`FeignConfig`、`JwtUtils` 相关方法）、可能触及引用上述类型的测试或配置。
- **行为**：对外 HTTP 契约上移除未校验的冗余请求头 `X-Internal-Token`；**不改变**已通过 HMAC 校验的内网调用成功路径（假设无调用方强依赖该头语义）。
- **文档**：`docs/internal-auth.md`、必要时 README 中与内网认证矛盾的片段。
- **风险**：若存在未入库的脚本或下游**硬依赖** `X-Internal-Token` 存在（即使服务端忽略），需在合并前完成全仓库与运维侧确认；若有则列为 **BREAKING** 并单独沟通。当前仓库内主文档已指向 HMAC 方案，预期为低风险清理。
