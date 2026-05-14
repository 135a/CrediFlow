## 1. 数据库更新

- [x] 1.1 在 `loan_application` 表中增加人工审核审计字段：`manual_reviewer_id`, `manual_review_reason`, `manual_review_time`。

## 2. 状态机与实体类更新 (loan-application-service)

- [x] 2.1 在借款申请单的状态枚举中增加 `PENDING_MANUAL_REVIEW` 待人工审核状态。
- [x] 2.2 更新 `LoanApplication` 实体类，映射新增的数据库审计字段。

## 3. 风控决策与拦截支持 (credit-risk-service)

- [x] 3.1 增加 `MANUAL_REVIEW` 的风控决策结果枚举类型。
- [x] 3.2 调整风控审批处理逻辑，支持在特定条件（或 AI 建议）下返回 `MANUAL_REVIEW` 以替代原有的刚性拒绝。

## 4. 借款申请核心流转与内部接口 (loan-application-service)

- [x] 4.1 更新风控回调处理：当决策为 `MANUAL_REVIEW` 时，将状态推演至 `PENDING_MANUAL_REVIEW` 且不触发后续自动放款事件。
- [x] 4.2 开发给 Admin 层调用的内部查询 API：支持按状态分页查询申请列表以及申请详情上下文。
- [x] 4.3 开发给 Admin 层调用的内部审批 API：实现状态从 `PENDING_MANUAL_REVIEW` 到 `APPROVED` / `REJECTED` 的人工干预，并持久化操作人与审批理由。

## 5. 管理端 BFF 聚合开发 (admin-bff-service)

- [x] 5.1 初始化并补全 `admin-bff-service` 的包结构、Web MVC 基础配置以及鉴权拦截器。
- [x] 5.2 编写针对 `loan-application-service` 的 OpenFeign 客户端 `LoanApplicationAdminClient`。
- [x] 5.3 开发 `AdminLoanApplicationController`，对外暴露供前端调用的 RESTful API：`/api/admin/loan-application/pending-list` 及 `/api/admin/loan-application/{id}/review`。
