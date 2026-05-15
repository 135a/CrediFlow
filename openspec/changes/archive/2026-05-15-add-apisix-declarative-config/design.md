# 设计：本地声明式 APISIX 与 Compose 自动同步

## Context

- **现状**：`docker-compose.yml` 已编排 `etcd-apisix` + `apisix:3.9.0-debian`，仅挂载 `infra/apisix/config.yaml`（`config_provider: etcd`），**未**随仓维护路由/插件；`app-bff-service`、`admin-bff-service` 已在 `internal` 网络，BFF 容器 **8080** 暴露。
- **签发端**：`user-service` 使用 `ExternalJwtUtils`（HS256，`sub` 为 `userId` 字符串、含 `role` claim；密钥当前为类内常量——与提案一致，本地网关 **MUST 使用同一对称密钥** 才能验签并稳定推导 `X-User-Id`）。
- **约束**：仅本地 Compose；你已接受 **演示级密钥可入库**，但仍需在文档与实现上与 `deployment-compose`「生产密钥不进 compose」区分。

## Goals / Non-Goals

**Goals:**

- 在 `infra/apisix/` 维护 **可审** 的声明式路由片段（JSON 或 YAML，以实现阶段选型为准），覆盖 **`/api/app/**` → `app-bff-service:8080`**、**`/api/admin/**` → `admin-bff-service:8080`**。
- **`docker compose up` 后无需手工 Dashboard**：通过 **独立 init 服务或等价一次性 Job**，在 APISIX Admin API 就绪后 **幂等写入** etcd（重复 `up` 不产生不可合并状态，或采用固定 `id` 覆盖策略）。
- 南北向行为与 `gateway-apisix` 主规格对齐：**JWT 校验** + **通过后注入 `X-User-Id`**（及可选 `Authorization` 剥离/透传策略在 tasks 中写死一种）。

**Non-Goals:**

- 不定义生产 GitOps 唯一真相源；不扩展 **`/api/internal/**`** 边缘路由（与提案一致）。
- 不在本设计阶段改写 `user-service` 签发逻辑（除非实现时发现 claim 名不一致，再单开任务）。

## Decisions

| ID | 决策 | 说明 | 备选 |
|----|------|------|------|
| **D1** | **同步方式：Admin API + init 容器** | 新增 Compose 服务（建议名 `apisix-init`），镜像选用 **`curlimages/curl`** 或带 `bash` 的轻量镜像；挂载 `infra/apisix/scripts/sync-routes.sh` 与声明式片段；`depends_on` **`apisix`**，循环探测 `9180` Admin 健康后，用 **`X-API-KEY`** 调用 `PUT /apisix/admin/...` 写入路由/上游/全局规则。理由：etcd 模式下行之有效、与 3.9 镜像兼容，无需改 APISIX 官方 entrypoint。 | **etcdctl 直写**：绕过 APISIX 校验，键格式易错，维护成本高。**standalone 单文件**：需改 `config_provider`，与当前 etcd 架构冲突大。 |
| **D2** | **Admin Key**：在 `infra/apisix/config.yaml` 显式增加 `deployment.admin.admin_key`（本地固定 demo 值） | 当前 `config.yaml` 未声明 `admin_key`；init 脚本依赖 Admin API，**必须**有稳定 key；与「演示级可入库」一致。**禁止**将该 key 用于任何公网暴露的 Admin 监听（本地 `9180` 仅开发机）。 | 从宿主 `.env` 注入：可增强，列为后续优化。 |
| **D3** | **鉴权插件：`jwt-auth`（HS256）** | 与 `ExternalJwtUtils` 一致；`jwt-auth` 配置 `algorithm`/`secret`/`key` 与 Java 对齐；校验失败 **401**、不转发上游。 | OIDC：本地过重，与当前签发端不匹配。 |
| **D4** | **`X-User-Id`：首选 `proxy-rewrite`，备选 `serverless-pre-function`** | 验签通过后，将 **`sub`（数字 userId 字符串）** 写入请求头 **`X-User-Id`**；若 APISIX 3.9 在 `proxy-rewrite` 中 **无法可靠引用** `jwt-auth` 解析结果，则采用 **单段 Lua** `serverless-pre-function` 读取 `ctx`/`ngx.var` 并 `set_header`，避免在路由层复制多份密钥逻辑。 | 仅用 `consumer` 模型：与无 consumer 的纯 JWT 流不匹配。 |
| **D5** | **声明式文件形态：按资源分片 JSON（推荐）** | Admin API 原生 JSON；可按 `upstream-app-bff.json`、`route-app.json`、`route-admin.json` 分片，脚本顺序 `PUT`。若团队更偏好 YAML，可在 tasks 中加 **一步 `yq` 转 JSON`**（增加镜像依赖），本设计不强制。 | 单文件 mega-json：diff 体验差。 |
| **D6** | **客户端伪造 `X-User-Id`** | `proxy-rewrite` / Lua 阶段 **覆盖** 客户端传入的同名头（若存在）；并在 `infra/apisix/README.md` 写明约定，与 BFF 信任模型一致。 | 信任客户端头：不安全，否决。 |

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| **init 与 apisix 竞态**（Admin 未就绪） | 脚本内 **退避重试** + `depends_on`；可选 `apisix` **healthcheck**（curl 9180/9080）。 |
| **重复 `up` 导致配置漂移**（手工 Dashboard + 文件双轨） | README **禁止**本地对同环境手改 etcd；`sync` 脚本使用 **固定 id** `PUT` 覆盖。 |
| **`proxy-rewrite` 变量名与文档不一致** | tasks 中安排 **一次 spike**：用真实 JWT `curl` 验证头；不行则切 **D4 备选**。 |
| **Admin 9180 暴露宿主机** | 与现状一致；文档标注 **仅本机开发**；生产由另一套网络策略处理（非本 change）。 |

## Migration Plan

1. **开发**：按 `tasks.md` 增加文件、`config.yaml` 补 `admin_key`、compose 增加 `apisix-init`、补 `infra/apisix/README.md`。
2. **首次验证**：`docker compose up -d etcd-apisix apisix` → 待 healthy → `up` BFF → 全量 `up`，对 `9080` 发带 JWT 请求，检查 BFF 访问日志是否出现 **`X-User-Id`**。
3. **回滚**：移除 `apisix-init` 与挂载脚本；`docker compose down -v` 清空 etcd 卷（**数据清空**风险仅限本地）；恢复仅 `config.yaml` 的旧行为。

## Open Questions

- **`jwt-auth` 与 `sub` 类型**：若 Java 签发 `sub` 非纯数字字符串，需在 tasks 与 BFF 约定一致（当前 `ExternalJwtUtils` 为 `String.valueOf(userId)`）。
- **未登录路径**：登录/注册是否走 **`/api/app/**` 白名单** 跳过 `jwt-auth`，需在路由层单独 `route`（priority）实现；具体路径以 `UserController` 映射为准，在 tasks 枚举。
- **是否挂载 `edge` 网络**：主规格提到 `edge`；当前 compose 仅有 `internal`。若要保持严格一致，可在后续变更拆网络；**本 change 不强制改网络模型**，以免牵连面过大。
