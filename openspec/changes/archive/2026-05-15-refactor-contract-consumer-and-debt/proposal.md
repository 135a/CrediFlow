## Why

在之前的安全巡检与数据库修补之后，我们发现 `loan-contract-service` 仍存在严重的事务风险和设计缺陷：MQ 消费者 `LoanApprovedConsumer` 在消费审核通过消息时缺乏幂等性检查，且混合了本地数据库操作与远端 Feign 调用（扣减额度），极易因分布式异常导致数据错乱。同时，合同签署流程在 HTTP API 和 MQ 中存在双入口冲突。此外，项目代码规范存在滥用 `RuntimeException` 和未导入全限定类名的问题。本变更旨在通过重构消费者逻辑与收敛业务入口，消除这些可能导致资损的深水炸弹。

## What Changes

- **实现消费者幂等性**：为 `LoanApprovedConsumer` 中的 `generateContract` 与 `generateReceiptAndPlan` 逻辑增加数据库维度的前置检查。
- **收敛合同生成与签署职责**：明确 `LoanApprovedConsumer` 仅负责创建状态为 `INIT` 的合同；真正的签署动作以及后续借据的生成只由用户通过 HTTP API (`LoanContractController.signContract`) 显式触发。
- **统一异常处理**：将业务逻辑中抛出的 `RuntimeException` 替换为全局标准的 `BusinessException`。
- **重构代码可读性**：优化 `LoanContractServiceImpl` 等文件，补充 import 语句并消除 `java.math.BigDecimal` 等全限定名。
- **清理无用配置**：从 `application.yml` 或 Config Server 中删除过期的 `crediflow.contract.interest-rate` 键值。
- **主键策略对齐**：在 `design.md` 中固化授信/合同/资金流水等核心表的 `ASSIGN_ID` 与分片、跨服务引用约束，实现与代码中 `@TableId` 策略一致。

## Capabilities

### New Capabilities
- `loan-contract-lifecycle`: 规范借款合同从审批通过后的生成、用户签署、到生成最终借据和还款计划的整体生命周期状态流转与入口控制。

### Modified Capabilities
- 

## Impact

- `loan-contract-service` 的消息消费层、合同与借据服务层。
- 系统整体稳定性和幂等容错能力大幅提升，彻底避免因重试或多入口导致的一笔贷款多份合同或重复放款的恶性 BUG。
- 授信、额度、资金流水等微服务在调整实体主键时，应以本变更 `design.md` 中「决议 3」策略表为约束，避免与分片及跨服务引用假设冲突。
