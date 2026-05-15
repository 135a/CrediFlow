## Context

`credit-risk-service` 中的两个核心控制器（`CreditInternalController` 和 `CreditAdminController`）由于早期开发时追求速度，大量引入了本应属于数据持久层（DAO）和业务逻辑层（Service）的代码。
另外，部分实体（如 `UserCreditQuota` 和 `CreditReviewQueue`）错误地使用了 `@TableId(type = IdType.AUTO)`，这将导致这些表的数据依赖数据库的递增序列。在未来 CrediFlow 需要实现异地多活或分库分表时，主键会立即发生冲突。

## Goals / Non-Goals

**Goals:**
- 将 `CreditInternalController` 和 `CreditAdminController` 中的所有 `LambdaQueryWrapper` 操作重构成 `Service` 层的标准接口调用。
- 将包含业务判断的 `evaluateLoanRisk` 逻辑提取至服务层。
- 替换上述两个存在冲突隐患的实体的 ID 生成策略，统一采用 MyBatis-Plus 提供的全局 ID（`IdType.ASSIGN_ID`）。

**Non-Goals:**
- 暂不引入独立的风控引擎规则引擎框架（如 Drools），目前仍保持以代码实现的逻辑隔离。

## Decisions

- **决议 1: Service 层重塑与承接**
  在 `CreditApplicationService` 接口和其实现类中新增：
  - 获取用户最近授信申请状态：`Map<String, Object> getLastApplicationStatus(Long userId)`
  - 提取申请历史记录（配合 Admin）
  
  在 `CreditService` 接口和实现类中新增：
  - 高级信贷查询：`Map<String, Object> getQuotaSummary(Long userId)`
  - 风控信号拦截：`void escalateRiskSignal(Map<String, Object> signalData)`
  - 借款评估判断：`String evaluateLoanRisk(Map<String, Object> req)`

- **决议 2: 全局雪花 ID 替换**
  对 `UserCreditQuota` 和 `CreditReviewQueue`，修改注解为 `@TableId(type = IdType.ASSIGN_ID)`。
  同步修改 `V1__init_credit_risk.sql` (如果有的话) 将相关的表 `id` 字段声明从 `AUTO_INCREMENT` 中去除（或者留给下一次 DB migration）。

## Risks / Trade-offs

- **迁移阵痛**：若是 `AUTO` 切换为 `ASSIGN_ID`，已有老数据 ID 不受影响，但如果是高并发环境此时切换会存在新旧 ID 的跨度增大。
- 当前重构单纯是职责层面的搬迁，对接口的 Request / Response 形态无破坏，前端和调用方（如 BFF）零感知。
