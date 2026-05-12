## Context

目前，系统中的 `credit-risk-service` 采用的是“所求即所得”的模型：用户发起授信申请后，系统同步调用 AI Agent，根据大模型返回的通过建议和预估额度，直接更新用户的活跃授信流水 (`cf_credit_result`)。这种设计无法支持业务审核的延迟、大模型的响应超时异常处理，也缺乏对于拒件（Rejected）原因的持久化保存及人为干预能力。因此，必须引入独立的“授信申请状态机”并将这一环节落库。

## Goals / Non-Goals

**Goals:**
- 将“授信申请记录”与“最终生效额度”的实体解耦。
- 实现 `PENDING` -> `APPROVED` / `REJECTED` 的状态机流转支持。
- 提供面向运营人员（Admin端）的申请记录审核查询 API，支持按日期范围筛选。
- 提供人工兜底干预接口，允许管理员将被 AI 拒绝的记录强行修改为通过，并触发额度发放。

**Non-Goals:**
- 不涉及大模型 prompt 提示词的调优。
- 本次暂不引入 Kafka/RocketMQ 做申请链路的完全异步削峰，依然保持 HTTP 同步调用或简易线程池异步，仅解决记录和状态机问题。

## Decisions

### 1. 实体拆分决策
- 现状：只有一个实体 `CreditResult`。
- 决策：新增实体 `CreditApplication`（表名 `cf_credit_application`），包含字段：`id`, `userId`, `applyAmount` (用户期望额度/系统默认请求), `suggestedAmount` (AI建议额度), `status` (PENDING, APPROVED, REJECTED), `auditReason` (机审或人工审批理由), `createdAt`, `updatedAt`。
- 替代方案：在 `CreditResult` 加 `status` 字段。放弃原因：`CreditResult` 的语义是“生效的额度账户”，里面记录了可用和已用额度，把被拒绝的申请也存进去会导致领域模型概念混淆。

### 2. 状态机流转流程
- 用户申请接口 (`POST /api/app/credit/apply`) 先在数据库插入一条 `PENDING` 状态的 `CreditApplication`。
- 调用 Agent `evaluateRisk`。
- 根据返回的 status (SUCCESS/REJECT) 更新 `CreditApplication`，记录 `suggestedAmount` 和 `auditReason`。
- 若为 `SUCCESS`，则额外插入一条 `CreditResult` 数据真正为用户创建额度。
- 若调用 Agent 超时或异常，可以抛出异常，利用 DB 事务回滚，或者保持 `PENDING` 状态待跑批/人工补偿（推荐后者，提升容错性）。

### 3. 后台干预接口设计
- 接口：`POST /api/admin/credit/application/{id}/approve`
- 逻辑：校验当前记录是否为 `REJECTED` 状态。如果是，更新状态为 `APPROVED`，追加 `auditReason` 为人工强行通过备注，然后生成 `CreditResult`。如果是 `APPROVED` 则幂等返回或报错。

## Risks / Trade-offs

- **[Risk] Agent 响应超时导致用户前端一直 Loading (假死)** 
  → **Mitigation**: 后期可将调用 Agent 的步骤彻底移入消息队列（RocketMQ）进行异步消费，前端提交后立即拿到一个 `applicationId` 并轮询查询结果。本次为控制范围先保持目前的同步，但设定合理的 Feign Timeout，超时后落库 `PENDING` 提示用户“系统审核中，请稍后查看”。
- **[Risk] 并发多次点击申请**
  → **Mitigation**: 必须在数据库加针对 `userId` + 状态的唯一约束，或者在 Java 侧利用 Redis 分布式锁，防止一个用户同时创建多笔申请。
