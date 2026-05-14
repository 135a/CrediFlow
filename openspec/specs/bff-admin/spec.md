## Purpose

TBD

## Requirements

### Requirement: 管理端 BFF 边界

`admin-bff-service` MUST 仅暴露面向 **运营 / 风控 / 系统管理员** 的 HTTP API；对外路径前缀 MUST 为 **`/api/admin/**`**（或由网关剥离前缀后一致）；MUST 与 `system-service` 的权限模型对齐（角色/权限校验在 BFF 或下游至少一处强制执行）。

#### Scenario: 终端用户令牌访问管理端被拒绝

- **WHEN** 请求使用终端用户 JWT 访问 `/api/admin/**`
- **THEN** 网关或 admin BFF MUST 返回 403

### Requirement: 与领域服务协作

Admin BFF MUST 通过 OpenFeign/HTTP 调用领域微服务；MUST 透传 `X-Request-Id`；批量或高危操作 MUST 记录审计上下文（操作者、目标 id）。

#### Scenario: 审计字段贯通

- **WHEN** 管理员发起一笔受控写操作经 Admin BFF
- **THEN** 下游调用 MUST 携带可识别操作者的可信头部或令牌内声明且 MUST 可被审计查询

### Requirement: 对接人工审核聚合能力

`admin-bff-service` MUST 对外暴露专用于人工审核的聚合 API (`/api/admin/loan-application/**`)，该接口组 MUST 接收前端管理台的请求并透传调用 `microservice-loan-application` 以获取审核列表及提交流转指令。

#### Scenario: 聚合查询贷款申请上下文

- **WHEN** 管理员通过管理台请求待审核列表
- **THEN** `admin-bff-service` MUST 向底层服务发起调用，并返回包含必要上下文的结构化待审列表
