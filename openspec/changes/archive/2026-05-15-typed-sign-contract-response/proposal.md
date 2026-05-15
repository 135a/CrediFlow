## Why

`signAndGenerateContract` 与 `LoanContractController#signContract` 使用 `Map<String, Object>` 传递签约结果，编译期无契约、演进时易漏改，与「签约」这一明确领域语义不匹配。需在保持对外 JSON 字段稳定的前提下，用强类型对象承载返回值，便于维护与后续扩展字段。

## What Changes

- 在 `loan-contract-service` 中新增签约结果 DTO（如 `SignContractResult`），包含当前业务已使用的字段（如 `status`、`message`），必要时预留扩展位。
- 将 `LoanContractService#signAndGenerateContract` 的返回类型由 `Map<String, Object>` 改为该 DTO；`LoanContractServiceImpl` 相应调整。
- 将 `LoanContractController#signContract` 的 `Result` 泛型改为该 DTO。
- **BREAKING（弱）**：若存在**本仓库外**的 Java 客户端直接依赖 `Map` 类型编译，将需跟随改签名；HTTP JSON 在字段名与取值与现有一致时，对前端/网关可保持兼容。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `microservice-loan-contract`：补充「合同签署 HTTP 响应体须为稳定、可类型化的契约」的规范表述（与实现中的 DTO 对齐）。

## Impact

- `loan-contract-service`：`LoanContractService`、`LoanContractServiceImpl`、`LoanContractController` 及新增 DTO 类所在包。
- 依赖该接口类型的其它模块（本仓库内已通过检索限定为上述路径）。
