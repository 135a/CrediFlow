# contract-data-model Specification

## Purpose

规范借款合同、借据与还款计划的基础数据结构、初始化默认值及核心状态机，确保放款生命周期中数据落盘的一致性与完整性。

## Requirements

### Requirement: 借款合同初始化状态及类型约束

`cf_loan_contract` 借款合同表 MUST 包含 `contract_type`（合同类型）字段，并在创建一条新合同时，其默认 `status` MUST 设为 `INIT`。

#### Scenario: 合同生成时的默认状态
- **WHEN** 当 MQ 消费者或 HTTP 接口生成新的借款合同时
- **THEN** 数据库底层及应用实体应默认将记录置为 `INIT`（而非 `GENERATED`），后续签署流程方可将其流转为 `SIGNED`。

### Requirement: 核心生命周期表结构就绪

系统在启动和运行 `generateReceiptAndPlan` 逻辑时，MUST 保证底层数据库中 `cf_loan_receipt`（借据表）和 `cf_repayment_plan`（还款计划表）已通过 Flyway 脚本正确初始化。

#### Scenario: 系统正常初始化放款表
- **WHEN** 部署启动 `loan-contract-service` 并连接数据库
- **THEN** Flyway MUST 执行包含借据和还款计划表的建表脚本，确保运行时相关 Mapper 操作不发生表不存在异常。
