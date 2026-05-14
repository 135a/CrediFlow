## Why

在现有的智能风控审批流程中，针对被风控规则或 AI 判定为“存在疑似风险但不足以直接拒绝”或“边界条件”的贷款申请，目前系统缺乏柔性处理机制。为了提高授信通过率并避免 AI 误判导致的高价值客户流失，需要引入“人工审核”机制。这允许后台风控人员对特定状态（如“转人工”）的申请进行二次审查与干预，从而提升最终的业务转换率与风控精准度。

## What Changes

- 在 `credit-risk-service`（风控服务）和 `loan-application-service` 中增加贷款申请单“待人工审核 (PENDING_MANUAL_REVIEW)”的状态机支持。
- 完善 `admin-bff-service` 模块，对外暴露提供给前端后台管理系统使用的：人工审核待办列表查询、审核单详情查询、以及审批通过/驳回的 HTTP 接口。
- 为相关数据表添加“人工审核人 ID”、“人工审核意见”、“审核时间”等审计追踪字段。
- 在现有的 AI Agent 风控决策引擎环节，支持输出并处理“需人工复核”的特殊决策结果。

## Capabilities

### New Capabilities
- `admin-loan-approval`: 提供给后台系统运营/风控人员的人工审核操作入口、工作台列表查询及审批流转功能。

### Modified Capabilities
- `loan-application`: 贷款申请核心状态机与模型更新，增加对人工审核状态流转的支持，以及相应的状态变更事件。
- `bff-admin`: 由于此前该模块为空壳，需要将其激活并在规范中声明对接 `admin-loan-approval` 业务。

## Impact

- **API 影响**: 
  - `admin-bff-service` 将新增 `/api/admin/loan-application/**` 路由组及相关接口。
- **微服务影响**: 
  - `credit-risk-service`: AI 审批结果后置处理逻辑更新。
  - `loan-application-service`: 申请单状态机流转逻辑更新，增加审批结果落库。
- **存储影响**: 
  - `loan_application` 核心表需增加关于人工审核的审计字段，或在扩展表中记录人工处理痕迹。
