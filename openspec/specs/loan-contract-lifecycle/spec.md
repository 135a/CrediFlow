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

合同、借据及资金侧还款计划等实体的主键 MUST 采用 ASSIGN_ID（分布式 ID），以满足分片布局与跨服务稳定引用；具体实体清单与理由见归档变更 `openspec/changes/archive/2026-05-15-refactor-contract-consumer-and-debt/design.md` 决议 3。

#### Scenario: 主键不因分片或迁移而破坏外部引用
- **WHEN** 其他服务通过合同或计划主键进行关联查询或回调
- **THEN** 主键值 MUST 在写入时已为全局唯一且可稳定传递的 ASSIGN_ID，MUST NOT 依赖单库单调递增语义作为跨边界标识。

### Requirement: 审批通过消息消费者的载荷与依赖卫生

处理 `LOAN_APPROVED` 事件的消费者 MUST 仅包含完成该流程所必需的依赖与逻辑；MUST NOT 保留未使用的远程客户端注入或解析后未参与业务分支的消息字段，以免误导维护者并放大消息契约变更时的故障面。

#### Scenario: 消费者不携带与授信扣减无关的客户端
- **WHEN** 审阅 `LoanApprovedConsumer` 及其依赖注入列表
- **THEN** MUST NOT 存在仅为历史路径保留、且在本消费者 `onMessage` 中从未调用的 `CreditClient`（或等价 Feign）字段。

#### Scenario: 不解析未消费的消息字段
- **WHEN** 合同预生成仅依赖申请单 ID 与用户 ID 即可完成
- **THEN** 消费者 MUST NOT 对 `payload` 内金额、期数字段做无下游使用的解析；若未来需要扩展，MUST 通过显式变更消息契约与服务接口完成。

### Requirement: 合同就绪事件的幂等发射

在 `LOAN_APPROVED` 消费流程中，向 `CONTRACT_READY`（或等价标签/主题）发出的通知 MUST 仅在「本次消费路径中新创建了状态为 `INIT` 的预签合同」时触发；当幂等逻辑判定合同已存在因而未执行插入时，MUST NOT 再次发出该就绪事件，除非存在经文档化的独立去重机制。

#### Scenario: 重复审批事件不产生重复就绪通知
- **WHEN** MQ 因重试或重复投递导致同一 `application_id` 与 `user_id` 的审批通过事件多次到达消费者，且首次消费已成功创建 `INIT` 合同
- **THEN** 后续消费 MUST 成功签收且不再次发送 `CONTRACT_READY`（下游据此可避免重复副作用）。

#### Scenario: 首次创建仍正常通知
- **WHEN** 首次消费且数据库中尚不存在对应合同记录
- **THEN** 系统在插入 `INIT` 合同成功后 MUST 发送一次 `CONTRACT_READY`，以便下游进入「合同可展示/可签约」流程。
