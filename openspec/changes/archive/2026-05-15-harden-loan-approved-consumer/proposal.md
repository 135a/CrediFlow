## Why

`LoanApprovedConsumer` 在上一轮评审中存在误导性代码（未使用的 Feign 客户端、解析后未使用的 payload 字段）、脆弱的消息体解析，以及在合同幂等返回时仍可能重复发出 `CONTRACT_READY` 的风险。本变更在不大改业务流程的前提下，收紧消费者实现与规范，降低运维误解与下游重复副作用的概率。

## What Changes

- 移除消费者中未使用的依赖与无效解析逻辑，或改为明确、安全的用法（与 `generateContract` 入参一致）。
- 为 `LoanLifecycleMessage` 的 `payload` 增加类型与必填字段校验，失败时记录清晰错误并采用可观测的失败语义。
- 明确 **「仅在应通知下游时发送 `CONTRACT_READY`」** 的策略（例如仅在新插入合同成功时发送，或在独立幂等键控制下发送），避免审批事件重试导致下游重复触发。
- 优化异常信息传递（在保留安全边界的前提下，便于排障），并补齐必要的中文注释说明背景。

## Capabilities

### New Capabilities

- 无（本变更仅扩展现有 `loan-contract-lifecycle` 行为约束。）

### Modified Capabilities

- `loan-contract-lifecycle`: 补充 MQ 消费者在「预生成合同」与「发出合同就绪事件」上的幂等与顺序要求。

## Impact

- `contract/loan-contract-service` 中 `LoanApprovedConsumer` 及可能的 `LoanContractService` 接口签名（若需区分「是否新建」以控制发事件）。
- 订阅 `CONTRACT_READY` 的下游（若发事件策略收紧，行为更符合「至多一次业务通知」语义）。
