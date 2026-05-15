## Context

在目前的系统中，`credit-risk-service` 中的多个重要微服务间调用接口（如额度扣减等）受到类级别的 `@RequestMapping("/api/app/credit")` 影响，实际上暴露成了外部网关可达且不受 `InternalAuthFilter` 管控的伪内网接口。同时，`loan-contract-service` 在处理核心放款业务逻辑时，由于缺乏 `cf_loan_receipt` 等建表脚本以及错误的状态枚举设定，导致系统在正常流程下完全无法流转。必须通过严格的控制拆分与数据库补丁来解决这些技术债务。

## Goals / Non-Goals

**Goals:**
- 将 `credit-risk-service` 中的内部 API 与外部 API 在 Controller 级别进行物理拆分。
- 使用 Flyway V2 增量脚本补齐所有缺失的合同域数据库结构。
- 修正 `LoanContract` 相关的状态标识和遗留冗余代码。

**Non-Goals:**
- 本次变更暂不重构 MQ 消息处理中的跨服务分布式事务（本地事务与远程调用的混用）。该问题将记录为技术债务，留待后续通过事件驱动架构/事务消息升级统一解决。

## Decisions

- **决议 1: 强制拆解 `CreditController`**
  - **Rationale**: 不要依赖方法级别的路径补充。拆分出 `CreditInternalController` 并标注 `@RequestMapping("/api/internal/credit")`，使得所有微服务间调用严格遵循安全路由，天然落入内网 HMAC 签名的强制验证范围。

- **决议 2: 采用增量式 Flyway 脚本修补 V1 残缺**
  - **Rationale**: 现有的 `V1__init_loan_contract.sql` 已处于部署状态，直接修改可能导致 checksum 不符。因此新增 `V2__init_loan_receipt_and_repayment_plan.sql`，不仅用于创建缺失的两张表，同时通过 `ALTER TABLE` 为 `cf_loan_contract` 追加 `contract_type` 列，并校正 `status` 字段的默认值为 `INIT`。

## Risks / Trade-offs

- **Risk: 修改内网接口路径导致调用方（如合同服务、应用层服务）Feign 请求失败。**
  - **Mitigation**: 在开发任务中，明确列出必须连带扫描所有依赖于 `CreditClient` 的调用方，并在编译期通过全局更新路径来保证微服务通信一致。
