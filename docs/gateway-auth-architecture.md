
# 网关集中鉴权与 X-User-Id 身份透传架构

本文档阐述 CrediFlow 平台为何采用「**APISIX 网关统一校验 JWT → 注入 `X-User-Id` → 下游服务读取 Header**」的架构，而非让每个微服务自行解析 JWT。

## 机制概要

```
客户端 ──[携带 JWT]──→ APISIX 网关 ──[校验 JWT，注入 X-User-Id]──→ BFF / 微服务
                           │
                           │ 校验失败 → 直接返回 401
```

1. **客户端**：登录后从 `user-service` 获取 JWT（HS256，`sub` = userId），后续请求携带 `Authorization: Bearer <jwt>`。
2. **APISIX 网关**：通过 `jwt-auth` 插件校验 JWT 签名与有效期；校验通过后，用 `serverless-pre-function` 从 payload 中提取 `sub`，写入 `X-User-Id` Header（**覆盖**客户端可能传入的同名头）；校验失败直接返回 401，请求不到达下游。
3. **BFF / 业务微服务**：从 `X-User-Id` Header 读取当前用户 ID，无需持有 JWT 密钥，无需解析 JWT。
4. **服务间调用**：通过 OpenFeign 透传 `X-User-Id` + `Internal-Auth` HMAC 签名（见 [internal-auth.md](internal-auth.md)），不再传递 JWT。

## 为什么不在每个服务中解析 JWT？

### 1. 密钥集中管控，降低泄露风险

JWT 签名验证需要持有密钥。如果每个微服务都持有 HS256 对称密钥：

- 密钥散落在多个服务的配置中，任一服务泄露即全局泄露
- 金融信贷场景下，密钥泄露可导致任意伪造用户身份，后果严重
- 换密钥需要所有服务同步变更，协调成本高且容易遗漏

**网关集中验签**：只有 APISIX 持有 JWT 密钥，下游服务不接触密钥。换密钥只改网关配置，风险面最小化。

### 2. 防止客户端伪造身份

如果下游服务直接从 JWT 解析 `userId` 并信任，攻击者只需拿到密钥就能伪造任意用户的 JWT。

而网关注入 `X-User-Id` 的模式下：

- `X-User-Id` 由网关在验签通过后**强制覆盖**写入，客户端自行传入的同名头会被忽略
- 下游服务处于内网，`X-User-Id` 只可能来自网关或受 `Internal-Auth` 保护的服务间调用
- 攻击者无法从外部伪造 `X-User-Id`，因为请求到不了下游

### 3. 鉴权逻辑统一，避免实现不一致

每个服务自己验 JWT，容易出现：

- 有的服务校验了过期时间，有的忘了
- 有的服务只验签名不验 claim，有的多验了 `role`
- 密钥版本不一致，导致部分服务验不过

**网关统一鉴权**：一套配置、一套逻辑，所有受保护路由的行为一致。下游服务只需 `request.getHeader("X-User-Id")`，零鉴权代码。

### 4. 减少重复计算，提升性能

JWT 签名验证（HMAC-SHA256）本身开销不大，但在高并发下，每个服务对每个请求都验一次就是浪费。网关验一次，下游零开销。

### 5. 服务间调用不需要 JWT

微服务间通过 OpenFeign 同步调用时，身份上下文通过 `X-User-Id` 透传，安全通过 `Internal-Auth` HMAC 签名保证（见 [internal-auth.md](internal-auth.md)）。JWT 在网关层已"消费完毕"，后续链路不再需要它。

如果服务间也透传 JWT：

- 每个服务都要验 JWT → 重复计算 + 密钥分散
- 内网调用不需要 JWT 的"跨域信任"特性，HMAC 签名更轻量
- JWT 体积大（Base64 编码），服务间每次传递增加带宽开销

## 与其他架构的对比

| 维度 | 每个服务自己验 JWT | 网关验 JWT + 注入 X-User-Id |
|------|-------------------|---------------------------|
| **适用场景** | 简单项目、无网关、服务少 | 有网关、微服务多、需要统一管控 |
| **密钥管理** | 每个服务持有 → 分散风险 | 只有网关持有 → 集中管控 |
| **换密钥** | 所有服务同步改 | 只改网关 |
| **鉴权逻辑** | 散落在各服务 | 网关统一，下游只读 Header |
| **性能** | 每个服务重复验签 | 网关验一次，下游零开销 |
| **安全边界** | 每个服务都是入口，都要防 | 网关是唯一入口，下游在内网 |
| **服务间调用** | 继续透传 JWT | 透传 X-User-Id + 内网签名 |
| **实现复杂度** | 上手简单 | 需要网关基础设施 |

## 本项目的具体实现

| 组件 | 职责 |
|------|------|
| `user-service` / `ExternalJwtUtils` | 签发 JWT（HS256，`sub` = userId，含 `role` claim） |
| APISIX `jwt-auth` 插件 | 校验 JWT 签名与有效期 |
| APISIX `serverless-pre-function` | 从 `ngx.ctx.jwt_auth_payload` 提取 `sub`，写入 `X-User-Id`（覆盖同名头） |
| BFF / 业务服务 | 从 `X-User-Id` Header 读取用户 ID |
| `FeignConfig` / `InternalAuthRequestInterceptor` | 服务间透传 `X-User-Id` + 注入 `Internal-Auth` HMAC 签名 |
| `InternalAuthFilter` | 校验内网调用签名，防止绕过网关直连 |

## 安全注意事项

1. **`X-User-Id` 覆盖**：网关 MUST 覆盖客户端传入的同名头，防止伪造。详见 `openspec/changes/add-apisix-declarative-config/design.md` D6 决策。
2. **内网隔离**：下游服务不应暴露在公网，`X-User-Id` 的信任前提是请求来自网关或受 `Internal-Auth` 保护的内网调用。
3. **密钥分级**：JWT 密钥（HS256 secret）与 `Internal-Auth` HMAC 密钥（`crediflow.internal.secret`）是不同的密钥，分别管理。
4. **本地开发**：本地 Docker Compose 中的 JWT 密钥为演示级固定值，**禁止原样用于生产**。详见 `infra/apisix/README.md`。
