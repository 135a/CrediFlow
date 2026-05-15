## 1. 修复合同服务路由暴露问题

- [x] 1.1 修改 `LoanContractController.java`，将其类上的 `@RequestMapping` 注解由 `/api/app/loan-contract` 更改为 `/api/internal/contract`。

## 2. 编译验证

- [x] 2.1 运行全局 `mvn clean compile -DskipTests`，确保路由修改未导致其他依赖服务的编译错误。
