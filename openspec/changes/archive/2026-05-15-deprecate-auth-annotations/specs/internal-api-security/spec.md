## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: @Inner 与 @IgnoreAuth 注解支持
**Reason**: 已全面向基础设施层和路径规范（Path Convention）转移，在网关层完成拦截。保留这些无 AOP 消费者的注解会导致开发者产生误导性的安全感。
**Migration**: 删除所有引用。如果需要限制仅内网可访问，请使用 `/api/internal/` 作为接口前缀；如果需要接口对公网免鉴权（无 JWT），请在 APISIX 网关的 `jwt-auth` 插件白名单中予以配置。
