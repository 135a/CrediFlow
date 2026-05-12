# APISIX：App / 管理端 分流示例

网关将 **两套路由** 指向两个 BFF 上游（Compose 内服务名需与实际一致）。

| 路径前缀 | 上游服务名（示例） | 说明 |
|----------|-------------------|------|
| `/api/app/*` | `app-bff-service:8091` | 终端用户 App |
| `/api/admin/*` | `admin-bff-service:8090` | 运营 / 风控 / 管理员 |

在 APISIX `config.yaml` 或通过 Admin API 创建 `Route`：`uri` 前缀匹配 + `upstream.nodes` 指向对应 BFF 容器/进程。

JWT：可对 `/api/admin/**` 使用更短 TTL 或额外 `role=admin` claim 校验；与 `gateway-apisix` spec 一致。
