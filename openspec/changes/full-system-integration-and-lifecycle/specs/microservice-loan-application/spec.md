## ADDED Requirements

### Requirement: 借款通过后的异步事件分发
借款申请被终审通过后，系统 MUST 向外部投递借款通过的异步领域事件，以解耦后续的合同生成与放款流程。

#### Scenario: 借款申请成功通过
- **WHEN** 风控与业务审核双重通过一笔借款申请
- **THEN** 系统 MUST 将借款单状态置为“处理中（生成合同阶段）”，并 MUST 向 MQ 发送 `LOAN_APPROVED_EVENT` 事件
