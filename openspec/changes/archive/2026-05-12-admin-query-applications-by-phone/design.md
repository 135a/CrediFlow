## Context

当前信贷系统处于微服务分表架构下，授信流水 (`cf_credit_application`) 和借款流水 (`cf_loan_application`) 都只存储了 `user_id`。用户的手机号 (`phone`) 存储在 `user-service` 的 `cf_user` 表中，并且按照安全规范采用了加密存储（MyBatis-Plus 的 `CryptoTypeHandler`）。为了支持运营管理人员通过手机号来检索业务申请单，必须解决跨微服务的数据关联查询问题。

## Goals / Non-Goals

**Goals:**
- 提供基于手机号码的授信与借款申请全局查询能力。
- 在不破坏微服务数据隔离原则的前提下，实现跨服务的查询条件过滤。
- 对外暴露清晰的 Admin REST API。

**Non-Goals:**
- 不支持按用户姓名模糊查询（姓名加密且存在重名问题，手机号具备精确唯一性）。
- 不引入 ElasticSearch 等复杂的宽表检索引擎（考虑到目前只是单维度的精确反查，引入 ES 成本过高）。

## Decisions

1. **跨服务条件转换策略**：
   - 决策：在 `user-service` 新增一个内部解析接口 `/api/internal/user/by-phone`。各业务系统（授信/借款）在接收到含有 `phone` 参数的查询请求时，**先通过 Feign Client 调用该接口将手机号转换为 `userId`**，然后再携带这个 `userId` 到自己的本地数据库中执行 `SELECT * FROM xxx WHERE user_id = ?`。
   - 理由：这是微服务架构中最经典的聚合查询解法。由于是精确查找单个手机号，反查 `userId` 开销极低（O(1)），既避免了跨库 JOIN，也无需做复杂的宽表同步。

2. **借款申请管理端 API 新增**：
   - 决策：在 `loan-application-service` 新增 `LoanAdminController`，提供 `/api/admin/loan/applications` 接口，支持 `phone`、`startTime`、`endTime` 及分页参数查询。
   - 理由：补齐之前缺失的借款审计运营视图。

3. **授信申请接口增强**：
   - 决策：在 `credit-risk-service` 的 `CreditAdminController` 现有的 `/api/admin/credit/applications` 接口上增加 `phone` 参数，做兼容性升级。

## Risks / Trade-offs

- **[Risk] 手机号未注册导致查询不到**：如果传入的手机号在 `user-service` 查不到对应的 `userId`。
  **[Mitigation]** 内部 Feign 接口应友善返回空或特定状态码，业务服务判断后直接返回空的分页列表结果，而不是报错阻断。
- **[Risk] `user-service` 挂掉引发雪崩**：查询操作对用户服务的强依赖。
  **[Mitigation]** 这仅仅是后台管理查询，并非 C 端核心链路，可容忍一定的不可用。Feign Client 可配置合理的超时时间。

## Migration Plan

1. 在 `user-service` 实现内部手机号转 `userId` 的接口。
2. 更新 `credit-risk-service` 与 `loan-application-service` 的 Feign 客户端。
3. 发布增强与新增的业务管理端 Controller。
