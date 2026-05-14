# 提案：本地声明式 APISIX 配置与 Compose 自动同步

## Why

当前仓库内难以审阅「JWT 是否在校验、`X-User-Id` 是否注入 BFF」等南北向行为，配置易落在 etcd / 手工操作而与 `gateway-apisix` 规格脱节。你方确认 **仅面向本地 Docker Compose**，并希望在 **`docker compose up` 时自动将声明式配置同步进 etcd**，使单人单仓即可版本化、可 diff、可对照规格验收。

## What Changes

- 在 `infra/apisix/`（或约定目录）新增 **声明式** 路由/上游/插件配置（YAML），覆盖本地 **`/api/app/**` → `app-bff-service`**、**`/api/admin/**` → `admin-bff-service`** 分流，与现有 `gateway-apisix` 规格一致。
- 调整 **`docker-compose.yml` 中 APISIX 相关服务**（或新增 **入口脚本 / init 容器**）：在依赖就绪后 **自动执行** 将声明式配置 **同步到 etcd** 的步骤（例如官方推荐的 `apisix` 声明式导入流程；具体命令在 `design.md` 选定），实现「**up 即生效**」，无需手工 Dashboard。
- **范围限定本地**：不声称本目录文件即生产唯一真相源；生产若另有 GitOps，可在 `design.md` 说明如何复用或分叉。
- **密钥**：你已确认 **可以进仓库**；仍须在实现中区分 **演示用固定密钥** 与 `deployment-compose` 对「生产密钥不得硬编码」的约束——本地示例可用占位或与 `user-service` 当前签发密钥 **显式对齐** 并在文档中标注 **禁止原样用于生产**。

## Capabilities

### New Capabilities

- 无：本变更不引入新的业务能力域目录名；交付物为 **基础设施声明文件 + Compose 编排行为**，对齐既有网关规格。

### Modified Capabilities

- `gateway-apisix`：在变更内 **delta spec** 中增补 **仅适用于本地 Compose** 的条款——声明式配置 MUST 体现 JWT 校验与 `X-User-Id` 透传 Scenario 的可验收形态（与主规格 `统一网关鉴权与身份透传` 一致，不削弱既有 MUST）。
- `deployment-compose`：在变更内 **delta spec** 中增补或澄清——本地编排 MUST 支持 APISIX 声明式配置的 **自动加载/同步**；敏感项若入库 MUST 限定为 **演示级** 并与 `.env.example` 策略兼容（与主规格「不在 compose 硬编码生产密钥」不冲突）。

## Impact

- **文件**：`infra/apisix/**`、`docker-compose.yml`、可能新增 `infra/apisix/scripts/**` 或镜像 entrypoint。
- **运行时**：首次/每次 `up` 与 etcd 的交互方式需固定，避免与手工 Dashboard 混用导致 **漂移**。
- **与 Java 对齐**：`user-service` 当前使用 `ExternalJwtUtils`（HS256、内置 demo secret）签发登录 JWT；本地 APISIX **`jwt-auth` 插件** 宜采用 **同一对称密钥与同一 claim 布局**（例如 `sub` = userId），否则网关验签通过但无法稳定注入 `X-User-Id`。这是你问题 **2** 的取向：**优先 `jwt-auth` + 与签发端算法一致**；若未来改为 RS256/OIDC，再单开变更切换插件链。
- **问题 3（`X-User-Id`）**：取向为 **`jwt-auth` 校验通过后，用 `proxy-rewrite`（或等价）将解析出的 `userId` 写入 `X-User-Id`**；若 `jwt-auth` 暴露的上下文变量与 header 注入语法在版本 3.9 下需借助 **serverless-pre-function** 做字段映射，在 `design.md` 二选一并锁定，避免实现阶段来回改。
- **问题 4（路由范围）**：取向为 **先只做 C 端与管理端 BFF 入口两条线**；**不把** `/api/internal/**` 等东西向内网签名的边缘路由塞进「本地演示声明式」的必要范围（除非你们明确要让本地也能从外网模拟打到 internal；默认避免扩大攻击面与配置复杂度）。

## 已确认的决策输入（来自对话）

1. **范围**：仅本地 Compose。  
5. **密钥**：允许写入仓库（实现时仍建议 README 标明演示级）。  
6. **同步**：`docker compose up` **自动**同步到 etcd。
