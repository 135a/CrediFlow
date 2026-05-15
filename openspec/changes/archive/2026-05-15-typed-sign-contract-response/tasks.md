## 1. DTO 与接口

- [x] 1.1 在 `loan-contract-service` 下新增 `SignContractResult`（或设计文档约定之名）DTO，字段至少包含 `status`、`message`，并加简短中文类注释说明用途。
- [x] 1.2 将 `LoanContractService#signAndGenerateContract` 返回类型改为该 DTO；`LoanContractServiceImpl` 内将所有 `Map` 拼装改为构造 DTO 返回。

## 2. HTTP 层

- [x] 2.1 将 `LoanContractController#signContract` 的返回类型由 `Result<Map<String, Object>>` 改为 `Result<SignContractResult>`（或所选 DTO 名）。

## 3. 验证

- [x] 3.1 执行 `mvn -pl contract/loan-contract-service -am clean compile -DskipTests`，确认无编译错误。
