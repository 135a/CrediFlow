## Context

当前 CrediFlow 系统采用 AI Data Agent 和风控规则引擎自动进行授信评估。系统将根据大模型的决策返回 `APPROVED` 或 `REJECTED`。这种“一刀切”的自动化处理模式，对于存在疑似风险但不足以直接拒绝的用户，会导致误杀。因此，业务方要求引入“人工审核”流程，允许风控人员对这部分订单进行介入。目前 `admin-bff-service` 仍是一个空壳模块，需要进行开发和激活，作为人工审核的前端接口支撑层。

## Goals / Non-Goals

**Goals:**
- 在不影响主自动化风控流程的前提下，将“需要人工干预”的单据拦截在 `PENDING_MANUAL_REVIEW` 状态。
- 为 `admin-bff-service` 提供获取审核列表、获取详情和提交审批决策的接口，并透传给底层服务。
- 保留完整的审批操作痕迹（审核人、审核意见、审核时间）满足审计合规要求。

**Non-Goals:**
- 不涉及开发完整的前端 Vue/React 界面页面（仅提供后端接口）。
- 不对现有的 `user-service` 和资金流转产生破坏性修改。
- 本次不对规则引擎的具体规则逻辑进行复杂改造，主要提供拦截通道。

## Decisions

- **状态机流转设计**:
  - `loan-application-service` 中的状态机新增 `PENDING_MANUAL_REVIEW` 状态。当 `credit-risk-service` 返回的 `RiskDecision` 为 `MANUAL_REVIEW` 时，单据进入该状态。
  - 通过人工审核接口（`admin-bff-service`），状态可流转为 `APPROVED`（机审拒绝，人工通过）或 `REJECTED`（彻底拒绝）。
- **人工审核数据落库**:
  - 在核心表 `loan_application` 或 `credit_evaluation` 中增加 `manual_reviewer_id`, `manual_review_reason`, `manual_review_time` 字段。
  - 理由：相比于新建关联表，直接在原表加字段能减少连表查询，且对于单一维度的业务状态（人工审核意见）来说足够轻量和直接。
- **BFF 架构职责**:
  - `admin-bff-service` 将作为聚合层，负责校验管理端人员的 JWT Token 或 Session，然后通过 OpenFeign 调用 `loan-application-service` 完成单据的审核和更新。
  - 下游微服务（`loan-application-service` 等）通过 Header 里的 `X-Agent-Source` 和 `X-Request-Id` 来接收并记录操作人上下文信息。

## Risks / Trade-offs

- **Risk: 单据积压问题** -> 若风控引擎过于严格导致大量单据转人工，审核积压将影响用户体验。
  - **Mitigation**: 建议在 `credit-risk-service` 中增加自动降级或超时拒绝的调度策略（未来功能），初期应先控制进入人工审核通道的规则命中率。
- **Risk: 并发审核冲突** -> 多个审核员同时审核同一张单子。
  - **Mitigation**: 借助 Redis 锁或利用单据的状态字段做乐观锁控制，当一个管理员已经认领或处理了单据时，拒绝其他人的修改。
