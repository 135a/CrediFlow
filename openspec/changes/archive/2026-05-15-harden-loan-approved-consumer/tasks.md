## 1. 服务契约与幂等信号

- [x] 1.1 修改 `LoanContractService` / `LoanContractServiceImpl`：将 `generateContract` 调整为返回「本次是否新插入合同」（如 `boolean` 或小型结果类型），在已存在合同时返回「未新建」且不写库。
- [x] 1.2 全局检索 `generateContract` 的调用方并适配新签名（含 `LoanApprovedConsumer`、`signAndGenerateContract` 等）。

## 2. 消费者清理与事件发射

- [x] 2.1 重构 `LoanApprovedConsumer`：移除未使用的 `CreditClient` 注入；删除对 `payload` 中未使用字段的解析逻辑（与 design 决议 2 一致）。
- [x] 2.2 在 `LoanApprovedConsumer` 中：仅当 `generateContract` 表明「新建成功」时调用 `rocketMQTemplate` 发送 `CONTRACT_READY`；否则记录幂等跳过日志并正常返回。
- [x] 2.3 为关键分支补充中文注释（说明为何仅在新建时发事件、与 MQ 重试的关系）。

## 3. 异常与验证

- [x] 3.1 对 `LoanLifecycleMessage` 中 `loanApplicationId`、`userId` 等必填字段做 null/非法校验，失败时抛出带明确信息的 `BusinessException` 并打错误日志（避免无意义重试时可由调用方策略决定，此处以可观测为先）。
- [x] 3.2 审视 `catch` 块：在合规前提下保留或传递有助于排障的根因摘要。

## 4. 验证

- [x] 4.1 在仓库根目录执行 `mvn -pl contract/loan-contract-service -am compile -DskipTests`（或全量 `mvn clean compile -DskipTests`）确认编译通过。
