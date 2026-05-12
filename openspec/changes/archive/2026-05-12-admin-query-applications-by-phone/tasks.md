## 1. 用户服务内部 API 支持

- [x] 1.1 在 `user-service` 中实现内部服务接口 `GET /api/internal/user/by-phone`，接收明文/密文手机号参数，精确匹配并返回对应的 `userId`。如果未找到用户，返回优雅的空值或特定状态码。

## 2. 授信申请查询接口改造

- [x] 2.1 修改 `credit-risk-service` 的 `UserClient`，增加调用 `user-service` 内部 `/api/internal/user/by-phone` 的 Feign 声明。
- [x] 2.2 修改 `credit-risk-service` 的 `CreditAdminController`，在现有的 `GET /api/admin/credit/applications` 方法中新增非必填参数 `phone`。
- [x] 2.3 修改对应的 Service 层查询逻辑：如果传入了 `phone`，首先通过 `UserClient` 获取 `userId`。如果获取不到，直接返回空的分页列表；如果获取到，则在 `cf_credit_application` 查询条件中增加 `user_id = {userId}` 的精确匹配。

## 3. 借款申请全新管理端 API 落地

- [x] 3.1 修改 `loan-application-service` 的 `UserClient`，增加调用 `user-service` 内部 `/api/internal/user/by-phone` 的 Feign 声明。
- [x] 3.2 在 `loan-application-service` 中新增 `LoanAdminController`，对外暴露 `GET /api/admin/loan/applications` 接口，支持分页、时间区间及 `phone` 参数。
- [x] 3.3 在 `loan-application-service` 的 Service 层实现查询逻辑：无 `phone` 时按时间降序分页返回全量借款申请；有 `phone` 时通过 `UserClient` 获取 `userId`，并加入 `user_id = {userId}` 查询条件。
