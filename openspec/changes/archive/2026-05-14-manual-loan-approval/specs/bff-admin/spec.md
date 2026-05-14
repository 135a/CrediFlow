## ADDED Requirements

### Requirement: 对接人工审核聚合能力
`admin-bff-service` MUST 对外暴露专用于人工审核的聚合 API (`/api/admin/loan-application/**`)，该接口组 MUST 接收前端管理台的请求并透传调用 `microservice-loan-application` 以获取审核列表及提交流转指令。

#### Scenario: 聚合查询贷款申请上下文
- **WHEN** 管理员通过管理台请求待审核列表
- **THEN** `admin-bff-service` MUST 向底层服务发起调用，并返回包含必要上下文的结构化待审列表
