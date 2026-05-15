## 1. 领域基础（枚举、常量、审计字段）

- [x] 1.1 新增 `CreditResultStatus` 枚举并改造 `CreditResult` 实体与 `getActiveCredit` 等查询使用枚举。
- [x] 1.2 新增 `CreditQueryConstants`（含 `NOT_APPLIED`）等查询哨兵常量；确认不进入 `CreditApplicationStatus`。
- [x] 1.3 在 `crediflow-common`（或 credit 模块）新增 `BaseEntity` + `MetaObjectHandler`，授信相关实体继承并移除冗余手写 `setCreatedAt`/`setUpdatedAt`（保留业务必须的 `updatedAt` 显式刷新处若需）。
- [x] 1.4 新增 `AgentFallbackMessages`（或 properties），重构 `AgentClientFallback` 使用集中文案。

## 2. DTO 与 Service 契约

- [x] 2.1 新增 API DTO：`CreditApplyResponse`、`CreditApplicationStatusView`、`CreditApplicationResultView`、`QuotaSummaryResponse` 及请求 DTO（`QuotaDeductRequest`、`RiskSignalEscalateRequest`、`LoanRiskEvaluateRequest`、`LoanReviewEnqueueRequest`）。
- [x] 2.2 改造 `CreditApplicationService` 接口与 `CreditApplicationServiceImpl`：去掉 `Map` 返回，使用 View + 哨兵常量。
- [x] 2.3 改造 `CreditService` 接口与 `CreditServiceImpl`：`getQuotaSummary`、`escalateRiskSignal`、`evaluateLoanRisk`、`enqueueLoanReview` 等改用 DTO。

## 3. Controller 层

- [x] 3.1 重构 `CreditInternalController`：所有端点 `Result<DTO>`，请求体使用 `@RequestBody` 强类型；删除内联 `Map` 拼装与行尾冗余注释。
- [x] 3.2 检查 `CreditController` 与内部 DTO 序列化字段一致（必要时 App 端返回字段仍为 code 字符串）。

## 4. 调用方与 DDL 文档

- [x] 4.1 检索并适配消费 `/api/internal/credit/*` 的 Feign 客户端（如 `loan-application-service` 的 `CreditClient`）至新 DTO（保持 JSON 兼容）。
- [x] 4.2 更新 `V2__add_credit_application.sql` 或补充 migration 注释，使 `status` 列说明与 `CreditApplicationStatus` 全集一致。

## 5. 验证

- [x] 5.1 执行 `mvn -pl credit/credit-risk-service,loan/loan-application-service -am clean compile -DskipTests` 通过。
