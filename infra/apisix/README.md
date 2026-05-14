# 本地 APISIX 声明式配置（CrediFlow）

本目录用于 **Docker Compose 本地开发**：将路由、Consumer 等以 JSON 声明，经 `scripts/sync-routes.sh` 在 **`apisix-init`** 容器内调用 **Admin API** 写入 etcd，实现与 `gateway-apisix` 规格对齐的 **JWT 校验** 与 **`X-User-Id` 注入**。

## 目录说明

| 路径 | 说明 |
|------|------|
| `config.yaml` | APISIX 主配置（etcd、`admin_key` 等）；**`9180` Admin 仅用于本机开发**，勿对公网暴露。 |
| `conf.d/*.json` | 分片声明：Consumer、`/api/app/**`、`/api/admin/**` 路由等。 |
| `scripts/sync-routes.sh` | 等待 Admin 就绪后按固定顺序 `PUT` 各 JSON。 |
| `VERIFICATION.md` | 手工验收步骤（curl / 登录取 token）。 |

## 与 `user-service` 签发 JWT 的约定

- **算法**：HS256。  
- **对称密钥**：与 `ExternalJwtUtils.EXTERNAL_SECRET` 一致（见 `consumer-crediflow-jwt-demo.json`）。  
- **JWT `key` claim**：固定为 `crediflow-app-jwt`，用于匹配 APISIX Consumer 的 credential（`jwt-auth` 插件要求）。  
- **`sub`**：用户 id 字符串；网关在校验通过后写入 **`X-User-Id`**（见 `route-app-catchall.json` / `route-admin-catchall.json` 中 `serverless-post-function`）。

## 使用方式

1. 启动：`docker compose up -d`（`apisix` 健康后 **`apisix-init`** 会自动同步一次）。  
2. **禁止** 在同一本地 etcd 上再用 Dashboard 手改路由后不回写仓库，否则会产生 **双轨漂移**；应以本目录 JSON 为唯一来源并重新执行 sync。  
3. 仅重跑同步（APISIX 已运行）：  
   `docker compose run --rm apisix-init`  
   （若 compose 中 `apisix-init` 为一次性服务，也可用 `docker compose run --rm` 等价镜像与挂载再执行脚本，以你本地 compose 版本为准。）

## 演示密钥风险

`config.yaml` 中的 **`admin_key`** 与 `conf.d` 中的 **JWT secret** 均为 **演示级**，**禁止**用于生产或任何可从外网访问的 Admin 端口。
