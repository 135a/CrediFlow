## Why

在近期的架构巡检中，我们发现了严重的安全隐患与可用性阻断问题：`credit-risk-service` 中的核心内部接口因 `CreditController` 类级注解配置不当而暴露于网关侧，且未受内网 HMAC 验签保护；此外，`loan-contract-service` 缺失关键的数据库建表脚本和状态机对齐，导致放款审核流程在生成借据阶段必定崩溃。本变更旨在彻底修复这些深层次的架构缺陷和安全漏洞。

## What Changes

- **分离控制器边界**：重构 `CreditController`，将其内部使用的接口（如 `/internal/quota/deduct` 等）抽离至新的 `CreditInternalController`，并采用严格的 `@RequestMapping("/api/internal/credit")` 路径。
- **补齐数据库迁移脚本**：在 `loan-contract-service` 中新增 `V2__init_loan_receipt_and_repayment_plan.sql` 用于创建 `cf_loan_receipt` 和 `cf_repayment_plan` 表；并在 `V1` 脚本中补齐 `contract_type` 字段。
- **统一合同状态机**：将借款合同 `cf_loan_contract` 的默认状态由 `GENERATED` 纠正为 `INIT`，严格遵循 `INIT -> SIGNED -> ARCHIVED` 的流转逻辑。
- **清理幽灵代码**：彻底移除冗余且无用的本地文件生成工具类 `PdfGeneratorUtil.java` 及其附带的本地模板。

## Capabilities

### New Capabilities
- `contract-data-model`: 定义借款合同、借据与还款计划的基础数据结构及核心状态机。

### Modified Capabilities
- `internal-api-security`: 补充类级别 `@RequestMapping` 路由配置的最佳实践，防止因 Controller 混用导致的内网防线失守。

## Impact

- 消除 `credit-risk-service` 存在的越权调用高危漏洞。
- 修复 `loan-contract-service` 无法完成放款生成借据的阻塞性 Bug。
- `PdfGeneratorUtil` 等死代码被清除，降低维护债务。
