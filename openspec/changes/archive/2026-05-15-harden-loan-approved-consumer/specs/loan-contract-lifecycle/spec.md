## ADDED Requirements

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
