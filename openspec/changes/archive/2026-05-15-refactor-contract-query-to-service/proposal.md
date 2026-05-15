## Why

用户在代码审查时指出，`ContractInternalController` 中直接使用了 `LambdaQueryWrapper` 进行数据库查询和组装返回业务逻辑。这种做法破坏了 MVC（Model-View-Controller）分层架构原则。Controller 层的职责应该是接收 HTTP 请求、参数校验、调用业务层接口并封装返回结果。数据库查询、条件拼装（尤其是 `LambdaQueryWrapper` 或 Mapper 操作）以及业务逻辑判断应该下沉到 Service 层。

## What Changes

- **职责下沉**：在 `LoanContractService` 中新增一个获取最新信用合同状态的方法（如 `getCreditContractStatus`）。
- **重构 Controller**：将 `ContractInternalController` 中的 `LambdaQueryWrapper` 查询逻辑和 `Map` 的封装逻辑整体迁移至 `LoanContractServiceImpl` 中，Controller 只负责调用 Service 并包装为统一的 `Result`。

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- 提升了代码的可维护性和复用性，遵循了更标准的三层架构规范。
- `ContractInternalController` 变得更加轻量化，聚焦于请求响应与路由。
