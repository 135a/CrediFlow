# app-bff-service（App 端 BFF）

- **用途**：移动 App / 终端用户的 **聚合与适配层**（注册登录、借还款查询等 C 端入口放此处聚合）。
- **对外路径约定**：经 APISIX 暴露为 **`/api/app/**`**（见 `infra/apisix/ROUTES-APP-ADMIN.md`）。
- **下游**：通过 OpenFeign 调用 `user-service`、`loan-application-service` 等领域服务（后续任务补齐）。
- **端口**：默认 `8091`（内网；对外统一走网关）。
