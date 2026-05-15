## 1. 业务逻辑下沉重构

- [x] 1.1 在 `LoanContractService.java` 接口中声明新方法 `Map<String, Object> getLatestCreditContractStatus(Long userId);`
- [x] 1.2 在 `LoanContractServiceImpl.java` 中实现该方法，将原来 `ContractInternalController` 内 `LambdaQueryWrapper` 查询及 `Map` 返回字典组装的逻辑完整迁移过来。
- [x] 1.3 在 `ContractInternalController.java` 中，将 `getCreditContractStatus` 接口的实现简化为仅仅调用上述 Service 方法。

## 2. 编译测试

- [x] 2.1 运行全局 `mvn clean compile -DskipTests` 验证修改是否破坏代码结构。
