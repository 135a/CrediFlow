# 设计：清理遗留 JWT 内网认证

## Context

- **现状（事实链路）**：`crediflow-common` 中 `FeignConfig` 注册了两个全局 `RequestInterceptor`：其一为 `InternalAuthRequestInterceptor`（`path + 毫秒时间戳` → `Base64(HMAC-SHA256)`，写入 `X-Timestamp` / `X-Internal-Sign`），与 `com.crediflow.common.filter.InternalAuthFilter`（`@Component`、`/api/internal/**`）及 Go 侧 `internalsign`、`fund-channel-gateway` 验签一致，与 `openspec/specs/internal-api-security/spec.md` 及 README 描述一致。
- **遗留路径**：`InternalAuthInterceptor` 每请求调用 `JwtUtils.generateInternalToken()` 并写入 `X-Internal-Token`；`com.crediflow.common.auth.InternalAuthFilter` 本可校验该头，但**从未注册为 Servlet Filter**，且拦截前缀为 `/internal/`，与业务实际 `/api/internal/**` 不一致。`docs/internal-auth.md` 仍描述 JWT 方案，与生产行为矛盾。
- **约束**：不得削弱对 `/api/internal/**` 的 HMAC 校验；不得改变 `crediflow.internal.public-paths` 等人脸回调白名单语义；变更前须全仓库确认无对 `JwtUtils` Bean、`X-Internal-Token` 的硬依赖。

## Goals / Non-Goals

**Goals:**

- 移除 JWT 内网发送端与死代码接收端，消除与 `filter.InternalAuthFilter` 的**同名类**混淆源。
- `FeignConfig` 仅保留与主链路一致的拦截器组合（含既有链路追踪等），避免每请求重复签发无用 JWT。
- 将 `docs/internal-auth.md` 与代码事实、OpenSpec 对齐。
- 在实施前通过静态检索确认符号级依赖为零后再删文件。

**Non-Goals:**

- 不修改 HMAC 签名算法、Header 名称、路径前缀或 `InternalAuthFilter`（`filter` 包）的验签逻辑。
- 不引入新的认证因子（例如「JWT + HMAC 双签」）；不调整 APISIX / 网关路由。
- 不迁移用户态 JWT（如 `user-service` 的 `ExternalJwtUtils`），与本变更无关。

## Decisions

| 决策 | 说明 | 曾考虑的替代方案 |
|------|------|------------------|
| **D1：删除 `InternalAuthInterceptor` 与 `FeignConfig` 中对应 `@Bean`** | 与提案一致；HMAC 拦截器已覆盖内网调用。 | 保留 Bean 但改为 no-op：仍占维护成本且易误导。 |
| **D2：删除 `com.crediflow.common.auth.InternalAuthFilter` 源文件** | 无注册点、路径不匹配，属死代码；删除优于 `@Deprecated` 长期悬挂。 | 仅标注 `@Deprecated`：同名陷阱仍在。 |
| **D3：删除整个 `JwtUtils` 类（`crediflow-common`）** | 当前仓库内该类**仅**含内部 JWT 生成/校验，且仅被上述 JWT 拦截器/过滤器引用；删除后无孤立 `@Bean` 依赖（实施前再执行一次全仓 `grep`/`compile` 确认）。 | 只删方法保留空壳：无其他用途，增加噪音。若实施时发现 `JwtUtils` 另有引用，则退化为「仅删内部方法并收缩类职责」——以编译与检索结果为准。 |
| **D4：`InternalAuthRequestInterceptor` 继续通过 `@Bean` 暴露，且保持 `@Value("${crediflow.internal.secret:...}")` 注入** | 与现有 `filter.InternalAuthFilter` 密钥来源一致；不在此变更中改为构造器注入，减少 diff。 | 改为纯构造器注入：可留作后续重构任务。 |
| **D5：文档以 HMAC 为主叙事** | `docs/internal-auth.md` 重写为：调用方由 `InternalAuthRequestInterceptor` 注入头；接收方为 `filter.InternalAuthFilter`；密钥与 `public-paths` 可简短指向 Nacos/README。 | 双轨文档（JWT + HMAC）：违背清理目标。 |

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|----------|
| 外部脚本或私有分支**强依赖**请求中存在 `X-Internal-Token`（例如监控采样） | 合并前全仓库 + 团队确认；必要时在变更说明中标注观察期。 |
| 误删仍被反射或 Spring 条件装配引用的类 | 全仓检索 + `mvn`/IDE 编译 `crediflow-common` 及抽样依赖模块。 |
| 删除 `JwtUtils` 后若未来需要「用户无关的短期内网 JWT」会重复造轮子 | 历史需求已由 HMAC 规范覆盖；若真有新场景应新开 change 与 spec，而非恢复本路径。 |

## Migration Plan

1. **开发阶段**：按 `tasks.md` 顺序改代码与文档；本地或 CI 全量编译受影响模块。
2. **合并与发布**：以更新后的 `crediflow-common` 版本为依赖的服务随常规发版滚动升级即可；**无数据库迁移**。
3. **回滚**：回退该 MR / 回滚依赖 `crediflow-common` 的版本号；无需数据回滚。
4. **配置**：`crediflow.auth.jwt-secret` 若仅在已删 `JwtUtils` 上使用，可从各环境 Nacos 样例或内部运维文档中**择机**清理说明（非阻塞本 MR，可在 tasks 中单列可选项）。

## Open Questions

- Nacos 或各 `application-*.yml` 中是否仍存在**仅**为内网 JWT 而文档化的 `crediflow.auth.jwt-secret` 条目：实施时一并检索，若无引用则更新示例配置或内部 wiki（可选）。
