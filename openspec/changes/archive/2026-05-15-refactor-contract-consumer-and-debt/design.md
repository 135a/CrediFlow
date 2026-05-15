## Context

`loan-contract-service` 目前在处理“授信或放款审核通过”的 MQ 消息 (`LoanApprovedConsumer`) 时存在巨大的架构隐患：
1. 没有做任何幂等处理。如果 MQ 因为网络抖动进行重试，会导致针对同一个 `application_id` 插入多份合同或生成多份借据，引发严重的业务资损。
2. 职责混淆。MQ 消费者直接一鼓作气执行了“生成合同 -> 生成借据和还款计划 -> 扣减用户额度”。由于远端 Feign 调用（扣除额度）如果超时抛出异常，本地的数据库事务很可能因为不是同一个事务管理器或设计缺陷无法完美回滚，或者造成数据部分落盘。
3. 同时，HTTP API 也提供了一个签署合同并生成借据的入口，这使得同一个业务流有两个并行的执行路径。
4. 代码层面上，滥用了不受控的 `RuntimeException`，未遵循框架层提供的 `BusinessException`。

## Goals / Non-Goals

**Goals:**
- 将借据生成的动作从 MQ 消费者中剥离，明确规定 **MQ 仅负责依据审批通过事件预生成状态为 `INIT` 的合同**。
- 为 `generateContract` 实现严格的数据库防重（幂等）检查。
- 重构全限定名以符合 Java 编程规范，并将异常统一转化为 `BusinessException`。

**Non-Goals:**
- 本次不对底层的事务协调器（如 Seata）进行引入。我们通过调整业务执行时机和入口边界（将高危的放款及扣额度操作收口到 HTTP 同步用户确认阶段或专用的放款最终事件中）来规避分布式事务的长程回滚难题。

## Decisions

- **决议 1: MQ 消费者降级为仅“初始化合同”**
  - **Rationale**: 审批通过后，立刻执行全部的借据生成和额度扣减是极其危险的（用户可能此时还未查阅和签署合同）。MQ 监听到 `LoanApprovedEvent` 后，只需要在 `cf_loan_contract` 中生成一条 `INIT` 的记录。后续由用户在前端点击“签署合同”通过 HTTP 接口调起完整的核心流程（更新为 `SIGNED` -> 扣减额度 -> 生成借据）。

- **决议 2: 数据库级别的兜底幂等性检查**
  - **Rationale**: 在 `generateContract` 方法内部，执行 `INSERT` 之前，必须先使用 `application_id` 和 `user_id` 在 `cf_loan_contract` 表中执行 `SELECT` 查询。若已存在且为 `INIT`，则直接忽略（Idempotent Return）。

- **决议 3: 核心业务表主键与分片相关 ID 策略（平台级对齐）**
  - **Rationale**: 分片、跨服务引用与安全域隔离要求主键全局唯一且与数据库 `AUTO_INCREMENT` 解耦；基础设施表可同样采用 ASSIGN_ID 以降低混用策略带来的运维复杂度。
  - **实体策略表**（第二、三列与输入一致：`AUTO` | `ASSIGN_ID`）：

| 实体 | AUTO | ASSIGN_ID | 说明 |
|------|------|------------|------|
| LoanContract | AUTO | ASSIGN_ID | 核心业务表，需分片；ID 被其他服务引用 |
| CreditReviewQueue | AUTO | ASSIGN_ID | 业务表，存在跨服务查询 |
| CreditScore | AUTO | ASSIGN_ID | 业务表，按 `applicationId` 关联 |
| UserCreditQuota | AUTO | ASSIGN_ID | 核心额度表，必须分片 |
| FundFlow | AUTO | ASSIGN_ID | 资金流水，必须分片，安全敏感 |
| RepaymentPlan（fund） | AUTO | ASSIGN_ID | 业务表 |
| LocalMessage | AUTO | ASSIGN_ID | 纯基础设施表，无跨服务引用（原稿「AUTO AUTO ASSIGN_ID」已收敛为与本表一致的两列） |
| MqIdempotentLog | AUTO | ASSIGN_ID | 纯基础设施表 |

  - **Note**: 实现上主键由 ASSIGN_ID 生成时，DDL 不应再依赖 `AUTO_INCREMENT`；若个别表仍为历史自增列，迁移与双写需单独变更跟踪。

## Risks / Trade-offs

- **Risk**: 扣减额度的调用如果由 HTTP 同步调用完成，用户签署合同时可能会面临短暂的延迟。
  - **Mitigation**: 对于小微金融场景，用户签署并最终发起放款的瞬间是业务关键点，短暂的 RPC 等待是合理的体验妥协，换来了系统最高级别的数据一致性。
