# gateway-apisix — 本变更规格增量（delta）

> 主规格：`openspec/specs/gateway-apisix/spec.md`  
> 适用范围：**仅本地 Docker Compose 演示/开发编排**；不修改生产环境唯一真相源之定义。

## ADDED Requirements

### Requirement: 本地声明式路由与 JWT、`X-User-Id` 行为可验收

在本地 Compose 默认启动路径下，仓库 MUST 在版本控制中提供 **APISIX 声明式路由/插件配置**（可为分片 JSON 或其它经 Admin API 导入的等价形态），使 **`/api/app/**`** 与 **`/api/admin/**`** 两条入口路由的行为满足主规格中 **JWT 与身份上下文传递** 及 **统一网关鉴权与身份透传** 的意图：网关 MUST 校验 JWT 签名与有效期；校验通过后 MUST 将 **`sub` claim 所表示的用户标识** 以 **`X-User-Id`** HTTP 头形式附加到转发至对应 BFF 上游的请求中；MUST NOT 信任客户端自行传入的 `X-User-Id` 作为最终身份（网关 MUST 覆盖或丢弃冲突值）。

#### Scenario: 有效 JWT 到达 App 前缀

- **WHEN** 客户端向本地 APISIX 入口请求 **`/api/app/`** 下任一受保护路径，且携带与 `user-service` 签发算法一致的 **有效 JWT**（`sub` 为用户 id 字符串）
- **THEN** 网关 MUST 返回上游业务可达时的正常响应族（2xx/4xx 由 BFF/业务决定），且转发至 `app-bff-service` 的请求 MUST 携带 **`X-User-Id`**，其值 MUST 等于该 JWT 的 **`sub`**

#### Scenario: 无效或过期的 JWT

- **WHEN** 客户端携带 **签名无效或已过期** 的 JWT 访问上述受保护路径
- **THEN** 网关 MUST 返回 **401 Unauthorized** 且 MUST NOT 将请求转发至 BFF 上游

#### Scenario: 管理端前缀分流

- **WHEN** 客户端请求路径以 **`/api/admin/`** 开头且 JWT 校验策略按本地声明式配置为「需有效令牌」（与主规格「管理端与 App 可区分策略」一致）
- **THEN** 网关 MUST 仅将请求转发至 **`admin-bff-service`** 上游且 MUST NOT 转发至 **`app-bff-service`**

### Requirement: 本地声明式配置与主规格可追溯对齐

本地声明式配置 MUST 在 `infra/apisix/`（或变更 `design.md` 最终锁定之目录）中维护，并在同目录或链接文档中说明：其与 **`gateway-apisix`** 主规格中 **App 与管理端路径分流**、**无效令牌被拒绝**、**身份信息透传** 等 Scenario 的对应关系，便于评审与回归。

#### Scenario: 评审者可仅读仓库完成核对

- **WHEN** 评审者仅打开本仓库中声明式配置与说明文档，不登录任何 Dashboard
- **THEN** 其 MUST 能够识别 JWT 插件、`X-User-Id` 注入及两条 BFF 分流是否已声明；若缺失，MUST 视为本变更未满足交付
