# 任务清单：清理遗留 JWT 内网认证

## 1. 依赖确认（合并前必做）

- [x] 1.1 全仓库检索 `X-Internal-Token`、`InternalAuthInterceptor`、`InternalAuthFilter`（注意区分 `auth` 与 `filter` 包）、`generateInternalToken`、`validateInternalToken`、`JwtUtils`（`com.crediflow.common.auth`），确认无测试、脚本、文档外硬依赖。
- [x] 1.2 确认 **App 端用户会话 / 登录 JWT** 未使用 `crediflow-common` 的 `JwtUtils`：用户态令牌应走各服务自有实现（如 `ExternalJwtUtils`）或 BFF/网关会话；本变更仅移除**微服务间 Feign** 注入的 `X-Internal-Token`，与 C 端会话维持无代码路径交集。

## 2. `crediflow-common` 实现收敛

- [x] 2.1 从 `FeignConfig` 中移除 `internalAuthInterceptor`（JWT）`@Bean` 及对应 import，保留 `InternalAuthRequestInterceptor` 与 `FeignTraceInterceptor` 等既有 Bean。
- [x] 2.2 删除 `InternalAuthInterceptor.java`。
- [x] 2.3 删除 `com.crediflow.common.auth.InternalAuthFilter.java`（勿误删 `com.crediflow.common.filter.InternalAuthFilter`）。
- [x] 2.4 删除 `JwtUtils.java`；若 1.1 发现仍有合法引用，则改为仅删除内部 Token 方法并调整调用方（以编译结果为准）。
- [x] 2.5 编译 `crediflow-common` 及至少一个依赖该模块的微服务模块，确保无 Spring Bean 装配失败。

## 3. 文档与配置说明

- [x] 3.1 重写 `docs/internal-auth.md`：以 HMAC + `X-Timestamp` / `X-Internal-Sign`、`InternalAuthRequestInterceptor`、`filter.InternalAuthFilter`、`crediflow.internal.secret` / `crediflow.internal.public-paths` 为主叙事；明确与 **用户登录 JWT / App 会话** 无关。
- [x] 3.2（可选）检索 `crediflow.auth.jwt-secret` 在示例配置与内部文档中的说明；若仅服务已删类，更新或标注废弃，避免运维误配。

## 4. 验证与收尾

- [x] 4.1 运行受影响模块相关单元测试 / 集成测试（若有）。
- [x] 4.2 在 MR 描述中注明：**不改变** `/api/internal/**` HMAC 校验行为；**不触及** App 端会话校验代码路径。

### MR 描述建议（复制到合并请求）

本变更移除 `crediflow-common` 中未启用的 JWT 内网 Feign 注入（`X-Internal-Token`）及死代码过滤器，删除仅被该路径使用的 `JwtUtils` 与 jjwt 依赖；**不改变** `filter.InternalAuthFilter` 对 `/api/internal/**` 的 **HMAC（`X-Timestamp` + `X-Internal-Sign`）** 校验行为，**不触及** App 端用户会话或登录 JWT（`ExternalJwtUtils` 等）。已验证 `mvn -pl common/crediflow-common -am package` 与 `mvn -pl user/user-service -am package`（`-DskipTests`）。
