# loan-contract-lifecycle Specification

## Purpose

本规范定义了授信/放款审批通过后，借款合同从自动初始化到最终签署的业务流转界限与幂等性保障原则。

## Requirements

### Requirement: 基于事件的防重初始化

系统 MUST 对所有因监听 MQ 事件而触发的合同生成操作实施严格的幂等检查。

#### Scenario: 重复的审批通过事件到达
- **WHEN** MQ 系统由于故障重发或多次推送同一笔 `application_id` 的 `LoanApprovedEvent` 到达 `LoanApprovedConsumer` 时
- **THEN** 服务端 MUST 在执行任何写入操作前校验该申请单是否已有对应的合同记录。如果已有，MUST 无视该重复事件并成功签收 MQ，避免由于唯一索引冲突导致无限重试。

### Requirement: 合同状态流转的动作隔离

借款流程中高度敏感的操作（如生成最终借据、执行远程额度扣除）MUST NOT 在后台静默自动完成。

#### Scenario: 控制核心资产生成的时机
- **WHEN** 系统处理借款流程时
- **THEN** 消息队列消费者 MUST 仅负责生成 `status='INIT'` 的预签合同草稿。后续真正的签约动作（变为 `SIGNED`）以及随后的借据生成与风险额度扣减，MUST 由用户侧调用专用的 HTTP 签署接口显式触发，以保证操作的原子性并明确用户的真实意愿。

### Requirement: 合同域主键策略与跨服务引用

合同、借据及资金侧还款计划等实体的主键 MUST 采用 ASSIGN_ID（分布式 ID），以满足分片布局与跨服务稳定引用；具体实体清单与理由见本变更 `design.md` 中的实体策略表。

#### Scenario: 主键不因分片或迁移而破坏外部引用
- **WHEN** 其他服务通过合同或计划主键进行关联查询或回调
- **THEN** 主键值 MUST 在写入时已为全局唯一且可稳定传递的 ASSIGN_ID，MUST NOT 依赖单库单调递增语义作为跨边界标识。
