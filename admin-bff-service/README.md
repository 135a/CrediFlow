# admin-bff-service（管理端 BFF）

- **用途**：运营 / 风控 / 系统管理员调用的 **聚合与适配层**（不承载领域账本权威逻辑）。
- **对外路径约定**：经 APISIX 暴露为 **`/api/admin/**`**（见仓库根目录 `infra/apisix/ROUTES-APP-ADMIN.md`）。
- **下游**：通过 OpenFeign 调用 `system-service`、`user-service` 等领域服务（Feign 客户端在后续任务中补齐）。
- **端口**：默认 `8090`（内网；由网关对外暴露 9080 等统一入口）。
