# internal-api-security Specification

## Purpose

定义微服务内网调用防越权、签名与敏感载荷最小化；以及 KYC v2 相关内部接口（人脸回调双层验签、风控反查、非生产后门）的安全约束。

## Requirements

### Requirement: 微服务内网零信任防越权调用

为了防止恶意用户绕过 APISIX 网关，通过内网直接请求后端的 HTTP 服务，系统 MUST 对内部所有的 Feign 调用附加自动安全签名（包含防重放时间戳与预共享密钥哈希）。服务端接收请求时 MUST 拦截并验证签名。Java → Go 资金网关的同步受理调用、以及 Go → Java 的桥接回调 HTTP（若启用）MUST 纳入同一套签名与时钟偏移校验策略；资金类请求体 MUST 最小化敏感字段（优先使用绑卡 token/引用 ID），完整卡号与证件明文 MUST NOT 出现在日志与异常栈中。

#### Scenario: 黑客伪造内网请求

- **WHEN** 攻击者或内部异常节点不经过 APISIX，直接通过 IP 访问 `system-admin-service` 的高危接口
- **THEN** 服务端拦截器 MUST 发现该请求缺失正确的 `X-Internal-Sign`，并拒绝访问，返回 401 Unauthorized

#### Scenario: 未签名调用 Go 资金网关

- **WHEN** 任一服务尝试无有效 `X-Internal-Sign` 调用 Go 资金网关内部放款或代扣接口
- **THEN** 网关 MUST 拒绝请求并 MUST 返回 401 Unauthorized

### Requirement: 人脸厂商回调入口的双层验签

系统 MUST 对人脸异步回调入口 `POST /api/internal/face-verify/callback` 同时实施两层验签：

1. **厂商签名**：由 `FaceVerifyProvider.verifySignature` 完成；签名规则、密钥、时间戳头由 Nacos 注入，MUST NOT 硬编码或与平台 `X-Internal-Sign` 共用密钥。
2. **平台内网约束（如启用 APISIX 反代 / 内网白名单）**：若回调由内部桥接代理，被代理后的请求 MUST 满足 `internal-api-security` 既有的 IP / 内网签名约束之一；MUST NOT 让外网直接打到内网回调接口。

`crediflow.internal.public-paths` MAY 将人脸回调路径从平台 `X-Internal-Sign` 强制校验中豁免，以便厂商无法持有内网密钥时仍能投递；豁免路径 MUST 仍由厂商签名与边缘防护兜底。

两层验签 MUST 在调用任何业务逻辑（落库、状态更新、领域事件投递）之前完成；任一层失败 MUST 返回 401 / 厂商约定的失败语义并 MUST 写一条安全审计事件。

#### Scenario: 仅厂商签名通过、内网约束不满足

- **WHEN** 攻击者从外网伪造一条厂商签名合法但绕过 APISIX 的回调
- **THEN** 系统 MUST 在内网入口拦截层拒绝（IP 不在白名单或缺少边缘标记头）；MUST NOT 进入 `FaceVerifyProvider.verifySignature` 之后的业务逻辑

#### Scenario: 厂商签名失败

- **WHEN** 回调 payload 被中间网络节点篡改导致厂商签名校验失败
- **THEN** 系统 MUST 返回 401；MUST NOT 写 `cf_face_verify_log` 业务终态；MUST 写安全审计

### Requirement: 风控与用户服务的内网反查接口

系统 MUST 把以下内部反查接口纳入 `internal-api-security` 既有签名校验：

- `POST /api/internal/risk/blacklist/check`（`credit-risk-service` 暴露给 `user-service`）
- `POST /api/internal/risk/kyc/lookup-by-fingerprint`（如启用，由 `user-service` 暴露给 `credit-risk-service`）
- `GET /api/internal/user/eligibility`（`user-service` 暴露给 `credit-application` / `loan-application`，返回 `{kycPassed, hasPrimaryBankCard}`）

这些接口 MUST 仅在内网可达（APISIX 不对外暴露）；调用方 MUST 携带合法 `X-Internal-Sign + X-Timestamp`；接口 body / 查询参数中 MUST 不出现明文姓名 / 身份证号 / 卡号 / 预留手机号；MUST 仅使用 `userId`、`idCardFingerprint`、`cardNoFingerprint`、`bindCardId` 等脱敏标识。

#### Scenario: 跨服务反查仅传指纹

- **WHEN** `user-service` 调用 `/api/internal/risk/blacklist/check`
- **THEN** 请求 body MUST 仅含 `idCardFingerprint` 字段；MUST NOT 包含明文姓名或身份证号

#### Scenario: 非法签名跨服务反查

- **WHEN** 调用方未带 `X-Internal-Sign` 或时间戳漂移超限调用 `/api/internal/user/eligibility`
- **THEN** 服务 MUST 返回 401；MUST NOT 进入业务逻辑

### Requirement: 非生产后门接口的强制访问控制

非生产环境下用于跳过人脸 / 直接置 KYC 通过的后门接口（如 `/api/internal/test/kyc/force-pass`）MUST 同时满足：

- 仅在 `@Profile("!prod")` 等价集合下注册；
- MUST 严格限制其 URL 必须符合内网隔离规范（如位于 `/api/internal/` 前缀下），且 MUST NOT 依赖 `@Inner` 等已被废弃的应用层注解；
- 走 `internal-api-security` 内网签名校验；
- 调用 MUST 写一条 `channel=BACKDOOR` 的审计流水。

生产环境集合下 MUST 完全不暴露这些接口（外部请求 MUST 返回 404）。

#### Scenario: 生产 profile 不暴露后门

- **WHEN** Spring 启动激活生产 profile 后端口监听
- **THEN** 任何外部请求 `/api/internal/test/kyc/*` MUST 返回 404；MUST NOT 出现在 Spring 注册的端点列表

### Requirement: 内网 Servlet 层主校验不得依赖 JWT 头（澄清）

对受 `internal-api-security` 约束、且由 Java 侧 Servlet `Filter` 实施强制校验的 `/api/internal/**` 路径（与 `com.crediflow.common.filter.InternalAuthFilter` 行为及 `crediflow.internal.public-paths` 白名单一致），系统 MUST 仅以 `X-Timestamp` 与 `X-Internal-Sign`（HMAC，与 `InternalAuthRequestInterceptor` 算法一致）作为**强制**的客户端侧认证凭据。系统 MUST NOT 将 `X-Internal-Token` 或等价的内部 JWT 请求头作为该类路径的**唯一**或**并联主**放行条件；MUST NOT 以「仅校验 JWT」的 Filter 替代或削弱上述 HMAC 校验（白名单路径仍 MUST 遵守主规格中对人脸回调等场景的既有约束）。

#### Scenario: 仅携带 JWT 而无有效平台签名

- **WHEN** 调用方请求某条须强制 `X-Internal-Sign` 的 `/api/internal/**` 路径，且仅携带或未携带 `X-Internal-Token`，但缺失、过期或篡改 `X-Timestamp` / `X-Internal-Sign`
- **THEN** 服务端 MUST 拒绝请求并 MUST 返回 401 Unauthorized；MUST NOT 因 JWT 头存在而进入业务逻辑

#### Scenario: 不得注册「仅以 JWT 放行内网 API」的并联主 Filter

- **WHEN** 某微服务向 Servlet 容器注册针对 `/api/internal/**`（或更广前缀且覆盖该路径）的认证 Filter
- **THEN** 该 Filter MUST NOT 将「校验 `X-Internal-Token` JWT 成功」作为放行该路径的充分条件；对须强制平台内网签名的接口，MUST 仍满足主规格中关于 `X-Timestamp` + `X-Internal-Sign` 的校验要求（白名单路径除外，且白名单 MUST 仍满足主规格对人脸回调等双层验签与边缘防护的约束）

### Requirement: @Inner 与 @IgnoreAuth 注解支持 (REMOVED)
**Reason**: 已全面向基础设施层和路径规范（Path Convention）转移，在网关层完成拦截。保留这些无 AOP 消费者的注解会导致开发者产生误导性的安全感。
**Migration**: 删除所有引用。如果需要限制仅内网可访问，请使用 `/api/internal/` 作为接口前缀；如果需要接口对公网免鉴权（无 JWT），请在 APISIX 网关的 `jwt-auth` 插件白名单中予以配置。

