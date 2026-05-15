## Why

随着系统架构向 API 网关和微服务声明式路由的演进，安全防线已前置至 APISIX 网关和 Filter 层（如基于 `/api/internal/` 路径的内网隔离控制）。目前应用层代码中残留的 `@IgnoreAuth` 和 `@Inner` 注解缺乏实际的 AOP/Filter 消费者，不仅沦为无用的死代码，还容易给开发者带来误导性的“安全错觉”。通过彻底移除这些失效注解并沉淀相关的架构哲学文档，能够统一并强化开发者的零信任安全心智。

## What Changes

- 彻底删除 `IgnoreAuth.java` 注解类，并移除所有代码（如 `UserController`、`FundFlowController`）中对该注解的引用。
- 彻底删除 `Inner.java` 注解类，并移除所有代码（如各类内部 Controller）中对该注解的引用。
- 在 `docs/` 目录下新增文档，沉淀关于“代码注解”和“路径网关拦截”两种安全哲学理念的对比分析。
- **BREAKING**: 明确微服务内部的拦截将不再支持也不再依赖这两种应用层注解。

## Capabilities

### New Capabilities
- `auth-architecture-philosophy`: 描述基于网关的零信任控制策略和基于代码注解的微服务防线控制之间的区别与优劣，用来指导开发者的安全架构思维。

### Modified Capabilities
- `internal-api-security`: 明确声明彻底移除应用层注解，内网访问的安全控制完全由基础设施与 URL 前缀（`/api/internal/`）和网关规范全面接管。
- `gateway-apisix`: 强调并确认由 APISIX 声明式路由接管 JWT 黑白名单与鉴权免登规则，业务侧无需注解。

## Impact

- 涉及 `user-service`、`fund-flow-service`、`credit-risk-service` 以及 `repayment-service` 中 Controller 类的 import 语句和类/方法头的元数据。
- 完全不会影响当前的运行时实际拦截策略（因为这些注解原先就没有被执行）。
- `crediflow-common` 中将丢失上述注解相关的两个源文件。
