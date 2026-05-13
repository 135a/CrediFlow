# microservice-loan-application（Delta）

## MODIFIED Requirements

### Requirement: 借款通过后的异步事件分发

借款申请被终审通过后，系统 MUST 向外部投递借款通过的异步领域事件，以解耦后续的合同生成与放款流程；后续实际触达资金方的放款 HTTP 调用 MUST 仅在风控与业务审核通过、合同就绪后，由 Go 资金网关统一执行，Java 借款申请服务 MUST NOT 直连资金方。

#### Scenario: 借款申请成功通过

- **WHEN** 风控与业务审核双重通过一笔借款申请
- **THEN** 系统 MUST 将借款单状态置为「处理中（生成合同阶段）」，并 MUST 向 MQ 发送 `LOAN_APPROVED_EVENT` 事件；后续放款执行链 MUST 在 `CONTRACT_READY_EVENT` 之后由资金流水/集成层调用 Go 资金网关受理放款，且 MUST NOT 在 Java 进程内发起对资金方的带签外呼
