## Purpose

TBD

## Requirements

### Requirement: 统一入口与零业务逻辑

Apache APISIX MUST 作为对外 HTTP(S) 唯一入口，将流量路由至后端上游服务；网关 MUST NOT 执行业务规则（包括但不限于授信计算、合同条款判定、还款金额计算）。

#### Scenario: 请求经网关到达上游

- **WHEN** 客户端向对外域名发起业务 API 请求
- **THEN** APISIX MUST 根据路由规则将请求转发至对应上游且 MUST NOT 修改业务语义字段（除协议与安全相关头部外）

### Requirement: JWT 与身份上下文传递

网关 MUST 校验 JWT 签名与有效期；校验通过后 MUST 将用户身份与角色声明以约定方式传递给上游（例如 `Authorization` 透传或网关注入可信头部，且上游 MUST 校验来源为网关链路）。

#### Scenario: 无效令牌被拒绝

- **WHEN** 客户端携带过期或签名无效的 JWT 访问受保护路由
- **THEN** 网关 MUST 返回 401 且 MUST NOT 将请求转发至上游业务服务

### Requirement: 限流、黑白名单与安全防护

网关 MUST 对单 IP 与单用户（若可解析）配置限流策略；MUST 支持 IP 黑白名单；MUST 启用面向 Web 攻击的基础防护能力（例如 SQL 注入/XSS 拦截策略由网关插件实现）。

#### Scenario: 触发限流

- **WHEN** 某客户端在滑动时间窗口内超过配置的请求阈值
- **THEN** 网关 MUST 返回 429 且 MUST 记录访问日志包含 request id 与客户端标识

### Requirement: 访问日志与审计最小字段

网关 MUST 为每条入口请求记录结构化访问日志，字段至少包含：时间戳、request id、HTTP 方法、路径、上游路由名、状态码、客户端 IP、用户主体标识（若存在）。

#### Scenario: 生成 request id

- **WHEN** 请求未携带上游可接受的关联 id
- **THEN** 网关 MUST 生成并注入全局 request id 供全链路关联

### Requirement: App 与管理端路径分流

网关 MUST 将路径前缀 **`/api/app/**`** 的请求路由至 **App 端 BFF**（`app-bff-service` 上游）；MUST 将 **`/api/admin/**`** 路由至 **管理端 BFF**（`admin-bff-service` 上游）；两套路由 MUST 使用可区分的限流与 JWT 校验策略（例如不同 `key` 或 claim 校验规则）。

#### Scenario: 管理端路径不可错误指向 App BFF

- **WHEN** 请求路径以 `/api/admin/` 开头
- **THEN** 网关 MUST 仅转发至 `admin-bff-service` 上游且 MUST NOT 转发至 `app-bff-service`

### Requirement: 统一网关鉴权与身份透传
网关 MUST 拦截所有向下的受保护流量，并在网关层完成 JWT 令牌的合法性与时效性校验；校验通过后，网关 MUST 解析出对应的身份信息，并通过特定的 HTTP Header 向下层微服务透传。

#### Scenario: 拦截未携带有效令牌的请求
- **WHEN** 外部客户端尝试直接请求 `/api/app/credit/apply` 接口而不携带有效 JWT
- **THEN** 网关 MUST 在路由分发前直接拦截请求并返回 401 Unauthorized

#### Scenario: 身份信息透传
- **WHEN** 带有有效 JWT 的请求到达网关
- **THEN** 网关验证成功后，MUST 将 Token 内置的 `userId` 抽取出来，以 `X-User-Id` 的 Header 形式附加到原请求中，然后再反向代理至后端的微服务集群

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

