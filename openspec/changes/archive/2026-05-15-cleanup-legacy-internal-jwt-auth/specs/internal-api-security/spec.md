# internal-api-security — 本变更规格增量（delta）

> 对应主规格：`openspec/specs/internal-api-security/spec.md`  
> 本文件仅描述**新增**的规范条款；不重复主文件中已生效的 HMAC 场景全文。

## ADDED Requirements

### Requirement: 内网 Servlet 层主校验不得依赖 JWT 头（澄清）

对受 `internal-api-security` 约束、且由 Java 侧 Servlet `Filter` 实施强制校验的 `/api/internal/**` 路径（与 `com.crediflow.common.filter.InternalAuthFilter` 行为及 `crediflow.internal.public-paths` 白名单一致），系统 MUST 仅以 `X-Timestamp` 与 `X-Internal-Sign`（HMAC，与 `InternalAuthRequestInterceptor` 算法一致）作为**强制**的客户端侧认证凭据。系统 MUST NOT 将 `X-Internal-Token` 或等价的内部 JWT 请求头作为该类路径的**唯一**或**并联主**放行条件；MUST NOT 以「仅校验 JWT」的 Filter 替代或削弱上述 HMAC 校验（白名单路径仍 MUST 遵守主规格中对人脸回调等场景的既有约束）。

#### Scenario: 仅携带 JWT 而无有效平台签名

- **WHEN** 调用方请求某条须强制 `X-Internal-Sign` 的 `/api/internal/**` 路径，且仅携带或未携带 `X-Internal-Token`，但缺失、过期或篡改 `X-Timestamp` / `X-Internal-Sign`
- **THEN** 服务端 MUST 拒绝请求并 MUST 返回 401 Unauthorized；MUST NOT 因 JWT 头存在而进入业务逻辑

#### Scenario: 不得注册「仅以 JWT 放行内网 API」的并联主 Filter

- **WHEN** 某微服务向 Servlet 容器注册针对 `/api/internal/**`（或更广前缀且覆盖该路径）的认证 Filter
- **THEN** 该 Filter MUST NOT 将「校验 `X-Internal-Token` JWT 成功」作为放行该路径的充分条件；对须强制平台内网签名的接口，MUST 仍满足主规格中关于 `X-Timestamp` + `X-Internal-Sign` 的校验要求（白名单路径除外，且白名单 MUST 仍满足主规格对人脸回调等双层验签与边缘防护的约束）
