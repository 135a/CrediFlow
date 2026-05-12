## Why

目前管理端只能按时间范围分页查询用户的授信申请，且缺乏对“借款申请”的全局查看视图。当客服或运营人员接到用户的电话进线时，迫切需要能够直接根据用户的**手机号码**来精准反查该名用户的所有“授信申请”和“借款申请”状态，以提高排查问题的效率。

## What Changes

- **授信申请查询强化**：升级原有的 `/api/admin/credit/applications` 接口，增加可选参数 `phone`。如果传入了手机号，则仅返回该手机号对应的授信申请。
- **新增借款申请查询能力**：新增借款服务的后台管理端接口 `/api/admin/loan/applications`，支持分页、时间范围以及按手机号 (`phone`) 查询借款申请明细。
- **用户 ID 解析**：为了在分库分表的微服务架构下支持按手机号查询，当传入 `phone` 参数时，后台接口需先调用 `user-service` 获取对应的 `userId`，再在当前业务库中基于 `userId` 进行精确查询。

## Capabilities

### New Capabilities
- `loan-admin-ops`: 借款申请的后台运营与查询能力，提供跨时间、按条件（手机号）检索借款流水。

### Modified Capabilities
- `credit-admin-ops`: 扩展现有授信申请查询接口，使其兼容按手机号过滤。
- `microservice-user`: 提供内部（Feign）接口，支持根据明文/密文手机号精确查找对应的 `userId`。

## Impact

- **API 变更**：
  - `GET /api/admin/credit/applications` (新增 `phone` 参数)
  - `GET /api/admin/loan/applications` (全新管理端 API)
  - `GET /api/internal/user/by-phone` (内部解析接口)
- **跨服务调用**：`credit-risk-service` 和 `loan-application-service` 需要依赖 `user-service` 来将手机号转换为 `userId`，以保持微服务数据边界清晰。
