## Why

`credit-risk-service` 在 Controller、Service 接口与 Feign 契约中仍大量使用 `Map<String, Object>`，申请/额度状态以魔法字符串散落，审计时间字段重复手写，Agent 降级文案硬编码在 Fallback 中。在已完成部分 Feign 与枚举改造的基础上，需要一次性统一授信域的类型化 API、状态枚举与横切审计能力，降低维护成本与跨服务契约漂移风险。

## What Changes

- 将 `CreditInternalController` 及相关 Service 的入参/出参由 `Map` 改为专用 DTO；`CreditController` 保持薄层并与内部契约对齐。
- `CreditApplicationService` 查询方法返回强类型 View/DTO（含 `NOT_APPLIED` 等查询哨兵常量，不写入 DB 状态枚举）。
- `CreditService` 剩余 `Map` 方法（额度摘要、风险上报、借款评估/入队等）改为 DTO；必要时按职责拆分子接口或保持 Facade。
- 新增 `CreditResultStatus` 枚举并与 `cf_credit_result.status` 对齐；核对 `CreditApplicationStatus` 与 `cf_credit_application.status` 取值全集。
- 引入 `BaseEntity`（`createdAt`/`updatedAt`）及 MyBatis-Plus 自动填充，逐步替换业务代码中的 `new Date()` 手写。
- Agent 降级固定话术集中到常量或配置文件，与已有数值型降级配置并列。
- **BREAKING（弱）**：内部 HTTP/Feign JSON 字段名保持不变时对外兼容；Java 调用方若依赖 `Map` 类型签名需同步改 DTO。

## Capabilities

### New Capabilities

- 无（能力约束落在既有 microservice 规范下扩展。）

### Modified Capabilities

- `microservice-credit-risk`：内部授信 API 与 Service 契约 MUST 使用强类型 DTO；禁止无约束 `Map` 作为服务层返回类型。
- `credit-application-lifecycle`：申请状态 MUST 与库表 `status` 列枚举值一致，并通过类型化状态表达（非魔法字符串）。
- `credit-quota-revolving`：授信结果/额度账户状态 MUST 使用封闭枚举（如 ACTIVE/FROZEN/EXPIRED）表达。

## Impact

- `credit/credit-risk-service`：Controller、Service、DTO、entity、enums、Feign、Agent Fallback、MyBatis 配置。
- 调用方：`loan-application-service` 等通过 Feign 消费 `/api/internal/credit/*` 的模块（需适配 DTO 或保持 JSON 字段兼容）。
- `common/crediflow-common`：可选 `BaseEntity`、共享查询哨兵常量。
- 数据库：以 VARCHAR 存枚举 `code`，不强制改列类型；迁移脚本注释需与代码枚举全集对齐。
