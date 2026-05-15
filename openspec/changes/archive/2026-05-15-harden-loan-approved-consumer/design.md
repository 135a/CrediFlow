## Context

`LoanApprovedConsumer` 负责消费 `LOAN_APPROVED` 标签消息，调用 `LoanContractService.generateContract` 写入 `INIT` 合同，并向 `CONTRACT_READY` 投递后续事件。当前实现存在未使用依赖、无效字段解析，以及「合同幂等返回仍发事件」可能与 MQ 重试叠加导致下游重复通知的问题。

## Goals / Non-Goals

**Goals:**
- 消除误导性代码路径，使消费者与 `generateContract` 的真实数据依赖一致。
- 对消息体做显式校验，避免静默 `ClassCastException` / `NPE`。
- 将 **`CONTRACT_READY` 的发送与「本次消费是否新创建了 INIT 合同」对齐**（或等价的全局幂等策略），避免重试场景下的重复下游触发。

**Non-Goals:**
- 不改变「仅 INIT 合同在 MQ、签署与借据在 HTTP」的总体边界（沿用已归档设计）。
- 不引入新的中间件（如 Redis 去重）除非实现阶段证明本地判断不足以满足目标；设计优先服务内返回值/标志位。

## Decisions

- **决议 1：`generateContract` 返回是否新建**  
  - **方案**：将 `generateContract` 改为返回 `boolean`（或小型结果对象），`true` 表示本次插入了新行，`false` 表示命中幂等已存在。消费者仅在 `true` 时发送 `CONTRACT_READY`。  
  - **Rationale**：无需额外存储即可区分「首次」与「重试/重复」，与数据库幂等查询同源。  
  - **备选**：独立发件表 / Redis setnx —— 运维成本高，暂缓。

- **决议 2：payload 处理**  
  - **方案**：若 `generateContract` 完全不需要金额、期数，则 **删除** 对消费者内 payload 的解析；若业务上预签合同展示需要，则改为经校验后传入服务层（需扩展接口）。当前代码路径未使用，**默认删除** 以降低契约耦合。  
  - **Rationale**：与 proposal 一致，先去掉无效解析；若产品后续要在 INIT 合同展示金额，再开变更扩展消息契约。

- **决议 3：未使用的 `CreditClient`**  
  - **方案**：从消费者类中 **移除** 注入与字段。  
  - **Rationale**：避免读者误以为 MQ 路径会调授信。

- **决议 4：异常**  
  - **方案**：校验失败或业务失败时，记录包含 `loanApplicationId` / `userId` 的结构化日志；抛出的 `BusinessException` 可携带简短 **英文或稳定错误码** 前缀 + 根因 message（避免日志泄露敏感字段的前提下适度保留 `getMessage()`）。  
  - **Rationale**：在合规允许范围内改善排障体验。

## Risks / Trade-offs

- **Risk**：历史上曾依赖「每次 LOAN_APPROVED 都发 CONTRACT_READY」触发下游的，若下游未做幂等，收紧后可能暴露「从未收到第二次」的差异 —— **Mitigation**：规范已明确「仅新建时发」；若下游曾依赖重复触发，需其侧改为幂等或改由查询驱动。  
- **Risk**：`generateContract` 签名变更导致调用方编译修改 —— **Mitigation**：仅合同服务内部与消费者，影响面可控。
