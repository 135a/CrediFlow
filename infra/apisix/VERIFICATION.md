# 本地 APISIX 验收说明（任务 5.x）

以下在 **宿主机** 执行，假定 `docker compose up -d` 已启动 `apisix`（`9080`）、`user-service`、`app-bff-service` 等，且 **`apisix-init` 已成功**（`docker compose logs apisix-init` 末尾为 `sync completed OK`）。

## 5.2 匿名路径（无 JWT）

登录应 **不** 被网关 `jwt-auth` 以「缺少令牌」直接 401（业务层可能返回 4xx，例如密码错误）：

```bash
curl -i -X POST "http://127.0.0.1:9080/api/app/user/login" \
  -d "phone=demo&password=demo"
```

若 HTTP 状态为 **401** 且 body 为 APISIX 默认 JWT 插件语义，则说明匿名路由未优先生效，需检查路由 **priority** 与 **uri** 是否被 `/api/app/*` 抢先匹配。

## 5.1 受保护路径 + `X-User-Id`

1. 先通过登录取得 token（上一步若登录成功，从 JSON 中取 `data` 字段 token；若用户不存在需先注册）。  
2. 携带 `Authorization: Bearer <token>` 调用 BFF 受保护接口，例如授信申请：

```bash
TOKEN="<粘贴 JWT>"
curl -i -X POST "http://127.0.0.1:9080/api/app/credit/apply" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{}"
```

- **期望**：非法或过期 token 时网关返回 **401**；合法 token 时请求到达 `app-bff-service`，且 BFF 日志中应能看到 **`X-User-Id`** 与 JWT 中 `sub` 一致（若 BFF 已打访问日志）。  
- 若 BFF 返回 500 等业务错误但 **非** 401，通常表示 **JWT 已通过网关**。

## 5.3 Admin 分流

```bash
curl -i -X GET "http://127.0.0.1:9080/api/admin/任意路径" -H "Authorization: Bearer $TOKEN"
```

- **期望**：连接到的上游为 **`admin-bff-service`**，而不是 `app-bff-service`（可通过 admin-bff 与 app-bff **不同错误体/端口侧日志** 区分；若 admin-bff 未实现该路径，可能为 404，但不应由 app-bff 产生 admin 专属响应）。

## 无效 JWT（401）

```bash
curl -i -X POST "http://127.0.0.1:9080/api/app/credit/apply" \
  -H "Authorization: Bearer invalid.token.here" \
  -H "Content-Type: application/json" \
  -d "{}"
```

**期望**：**401**。
