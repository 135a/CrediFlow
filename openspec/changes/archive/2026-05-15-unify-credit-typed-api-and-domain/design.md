## Context

授信服务已部分完成：`CreditApplicationStatus`、`ReviewSceneType`、`ReviewQueueStatus`、`UserClient`/`AgentClient` 强类型化。仍遗留 `CreditInternalController` 与 `CreditApplicationService`/`CreditService` 的 `Map` 返回、查询哨兵字符串、`CreditResult.status` 裸 `String`、实体时间字段手工赋值、Agent 降级中文案内联。

库表要点：
- `cf_credit_application.status`：`VARCHAR(32)`，代码枚举值为 `PENDING_HARD_RULES` … `COMPLETED`（迁移脚本注释需更新）。
- `cf_credit_result.status`：`VARCHAR(50)`，取值 `ACTIVE`/`FROZEN`（实体注释含 `EXPIRED`）。

## Goals / Non-Goals

**Goals:**
- Controller 仅做 HTTP 适配；DTO 定义请求/响应契约。
- Service 接口无 `Map<String, Object>` 返回（查询类用 View/DTO）。
- `CreditResultStatus` + 既有 `CreditApplicationStatus` 与 DB 通过 `@EnumValue` 映射。
- `BaseEntity` + `MetaObjectHandler` 统一 `createdAt`/`updatedAt`。
- `AgentFallbackMessages`（或 `agent-fallback.properties`）承载降级文案。

**Non-Goals:**
- 不在此变更重构 `loan-application-service` 全量 Map（仅适配受影响的 Feign 契约）。
- 不引入 Seata 或拆分微服务边界。
- 不修改对外 App 路径语义（`/api/app/credit/*` 行为不变）。

## Decisions

### 决议 1：DTO 包结构

- 路径：`com.crediflow.credit.dto`（API View）与 `com.crediflow.credit.dto.request`（入参）。
- 示例：
  - `CreditApplyResponse`（applicationId, status 为 String code 或枚举序列化）
  - `CreditApplicationStatusView`（status, applicationId, secondaryFaceRequired）
  - `CreditApplicationResultView`（status, auditReason, userSafeInsight）
  - `QuotaSummaryResponse`、`QuotaDeductRequest`、`RiskSignalEscalateRequest`、`LoanRiskEvaluateRequest`、`LoanReviewEnqueueRequest`

### 决议 2：查询哨兵与 DB 枚举分离

- `CreditQueryConstants.NOT_APPLIED`（或 `ApplicationQueryOutcome`）仅用于「无申请记录」的 API 响应，**不**加入 `CreditApplicationStatus` 枚举。

### 决议 3：`CreditResultStatus`

```java
public enum CreditResultStatus {
    ACTIVE, FROZEN, EXPIRED;
    @EnumValue private final String code;
}
```

`getActiveCredit` 查询条件改为枚举比较。

### 决议 4：`BaseEntity`

- 置于 `crediflow-common` 或 `credit-risk-service` 的 `entity.base`（优先 **common**，便于 user/loan 后续复用；若 common 改动面过大则先限 credit 模块）。
- 字段：`createdAt`、`updatedAt`；`@TableField(fill = INSERT / INSERT_UPDATE)`。
- 注册 `MybatisPlusConfig` + `MetaObjectHandler`。

### 决议 5：Agent 降级文案

- 类 `AgentFallbackMessages` 存放 `MANUAL_REVIEW_SUGGESTION`、`REJECTION_USER_SAFE_INSIGHT` 等常量。
- `AgentClientFallback` 引用常量；数值仍用 `@Value`。

### 决议 6：DDL 与枚举对齐（文档级）

- 新增或更新 migration 注释/可选 `ALTER` 说明，使 `cf_credit_application.status` 注释反映完整状态机；默认值避免使用已废弃的单独 `PENDING`（若线上仍为旧默认，应用层以写入枚举为准）。

## Risks / Trade-offs

- **Risk**：Feign 客户端编译失败 —— **Mitigation**：JSON 字段名与现网一致，仅 Java 类型变化。
- **Risk**：枚举反序列化历史脏数据 —— **Mitigation**：非法值在查询层打日志；必要时 TypeHandler 映射为 null 并走拒绝逻辑。
- **Trade-off**：`BaseEntity` 放 common 会牵动多模块 —— 本变更可先仅 credit 实体继承，common 仅放 `BaseEntity` 类。

## Migration Plan

1. 新增 DTO、枚举、常量、BaseEntity/Handler。
2. 改 Service 接口与实现，再改 Controller。
3. 适配 loan 等 Feign 调用方（若存在）。
4. `mvn -pl credit/credit-risk-service -am compile -DskipTests`。
5. 人工回归内部接口：apply、status、quota、deduct、review。

## Open Questions

- `BaseEntity` 是否本迭代推广到 `user-service` 全部实体：建议 credit 先行，user 另开 change。
