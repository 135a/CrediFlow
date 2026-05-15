## Why

在对 `credit-risk-service`（信用风控服务）进行探索时，我们发现了严重的微服务架构“反模式”。具体表现为：
1. **Controller 直接查写数据库**：`CreditInternalController` 和 `CreditAdminController` 存在大量越过 Service 层，直接构造 `LambdaQueryWrapper` 并调用 Mapper（如 `creditReviewQueueMapper.insert`）的行为。这导致核心业务逻辑脱离了 Spring `@Transactional` 事务的保护，极易产生脏数据，且无法被复用和进行有效的单元测试。
2. **硬编码业务逻辑**：Controller 充斥着“大泥球”代码，例如时间段判断、未接入验证的 Mock 数据以及硬编码返回文本等。
3. **分布式 ID 生成策略错误**：核心实体（如 `UserCreditQuota` 和 `CreditReviewQueue`）依赖 `@TableId(type = IdType.AUTO)`（MySQL 自增主键），一旦在微服务环境进行数据分片部署，将不可避免地引发主键冲突灾难。

为了防范生产环境潜在的数据安全和一致性问题，亟需进行深度的代码重构。

## What Changes

- **职责下沉隔离**：剥离 `CreditInternalController` 和 `CreditAdminController` 中的所有直接查询/写入逻辑，统一迁移至 `CreditService` 和 `CreditApplicationService`，确保 Controller 仅作为路由和分发层。
- **实体 ID 策略修正**：将 `cf_user_credit_quota` 和 `cf_credit_review_queue` 对应实体类的主键生成策略，从 `IdType.AUTO` 更换为微服务标准的 `IdType.ASSIGN_ID`。
- **代码净化**：清洗 Controller 中的临时逻辑，将规则判断统一规范化为服务层方法。

## Capabilities

### Modified Capabilities

- 信用核心服务（Credit Risk Service）
  - 获取信用状态 / 额度分配 / 预警通知等逻辑进行结构优化，业务链路不变。

## Impact

- 消除由于并发异常带来的分布式事务不一致隐患。
- 增强系统的单元测试友好度及未来分库分表的兼容性。
