# 任务清单：本地声明式 APISIX 与 Compose 自动同步

## 1. 基线与密钥

- [x] 1.1 在 `infra/apisix/config.yaml` 增加 **`deployment.admin.admin_key`**（本地演示固定值），并确保 **`9180`** 仅用于开发机（在 `infra/apisix/README.md` 写明风险边界）。
- [x] 1.2 核对 `ExternalJwtUtils` 的 **对称密钥字符串** 与 **HS256 / `sub` = userId** 行为；声明式 `jwt-auth` 配置 MUST 与之完全一致（含 claim 名）。

## 2. 声明式资源（JSON 分片 + 上游）

- [x] 2.1 在 `infra/apisix/conf.d/`（或 `design.md` 最终目录名）新增 **上游**：`app-bff-service:8080`、`admin-bff-service:8080`、`user-service:8080`（用于匿名登录注册链路的直连上游）。
- [x] 2.2 新增 **高优先级路由（无 `jwt-auth`）**：匹配 **`/api/app/user/register`**、**`/api/app/user/login`**、**`/api/app/user/auth/**`**（与 `UserController` 前缀一致），上游指向 **`user-service`**。
- [x] 2.3 新增 **默认 App 路由**：**`/api/app/**`**（置于低优先级或显式排除已声明的 user 子路径），上游 **`app-bff-service`**，插件链包含 **`jwt-auth`**；校验通过后 **`proxy-rewrite` 注入 `X-User-Id`**（取自 **`sub`**）；若经 **spike（任务 5.1）** 证实变量不可用，则改为 **`serverless-pre-function`** 写头，并删除无效 `proxy-rewrite` 片段。  
  **实施说明**：APISIX 3.9 下 `jwt-auth` 的 payload 未暴露为 `proxy-rewrite` 可用变量，已采用 **`serverless-post-function`（access 阶段，优先级低于 jwt-auth）** 从 `ngx.ctx.jwt_auth_payload` 读取 `sub` 并 `set_header('X-User-Id', ...)`。
- [x] 2.4 新增 **Admin 路由**：**`/api/admin/**`** → **`admin-bff-service`**，挂载与 App 可区分的 **`jwt-auth`** 策略（密钥或 `key` 可与 App 共用演示密钥，但路由 `id` 独立），同样在成功后注入 **`X-User-Id`**。
- [x] 2.5 所有写头逻辑 MUST **覆盖** 客户端传入的 **`X-User-Id`**（与 delta spec 一致）。

## 3. 同步脚本与 Compose

- [x] 3.1 新增 `infra/apisix/scripts/sync-routes.sh`（或等价）：等待 **`http://apisix:9180`** 可用，使用 **`X-API-KEY`** 对分片 JSON 执行 **幂等 `PUT`**（固定 `id`）；失败时 **非零退出** 便于 Compose 观察。
- [x] 3.2 在 `docker-compose.yml` 增加 **`apisix-init`** 服务：镜像选用 **`curlimages/curl`**（或等价），`depends_on` **`apisix`**，挂载脚本与 `conf.d`；**`restart: on-failure`** 或 **`depends_on` + `condition: service_started`** 按实测稳定性选型。
- [x] 3.3（可选）为 **`apisix`** 增加 **healthcheck**（探测 `9080` 或 `9180`），使 `apisix-init` 不必盲等。

## 4. 文档与变量发现性

- [x] 4.1 新增或更新 **`infra/apisix/README.md`**：目录结构、如何 **`docker compose up`**、如何 **单独重跑 sync**、**禁止** 与 Dashboard 双轨改配置、演示密钥声明。
- [x] 4.2 更新根目录 **`.env.example`**：若 `admin_key` 或 JWT 演示密钥改为环境变量注入，则列出变量名；若保持仓库硬编码 demo，则写明 **「当前为演示硬编码，变量表预留」** 并指向 README。

## 5. 验收

- [x] 5.1 **Spike**：用真实登录接口取得 JWT 后 **`curl` 访问** 受保护 **`/api/app/credit/apply`**（或任一需 `X-User-Id` 的 BFF 路径），确认 BFF 日志或响应证明 **`X-User-Id` 已到达**；无效 token 必须 **401**。（步骤见 **`infra/apisix/VERIFICATION.md`**，需在本地起全栈后执行。）
- [x] 5.2 **匿名路径**：不带 JWT **`POST`** **`/api/app/user/login`**（参数按接口约定）必须 **不被 `jwt-auth` 拦截**（返回业务语义或 4xx，但非网关 401「未授权令牌」类语义）。（见 **`infra/apisix/VERIFICATION.md`**。）
- [x] 5.3 **Admin 分流**：路径 **`/api/admin/`** 前缀请求 MUST **未** 转发到 **`app-bff-service`**（可通过错误上游或特征响应在验收步骤中说明验证方式）。（见 **`infra/apisix/VERIFICATION.md`**。）
