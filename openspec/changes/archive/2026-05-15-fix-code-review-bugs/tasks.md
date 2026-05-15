## 1. 核心 Bug 修复

- [x] 1.1 删除 `credit/credit-risk-service/src/main/java/com/crediflow/credit/dto/LoanRiskEvaluateRequest.java`，并更新所有相关的引用，使其指向 `common` 模块内的对应类。

## 2. 工程结构与包名规范化

- [x] 2.1 重构 `loan-application-service` 的包路径，从 `com.crediflow.application` 迁移至 `com.crediflow.loan`。
- [x] 2.2 删除 `user-service` 中错误放置的 `com/crediflow/Demo.java` 和重复的包 `com.crediflow.users`（若有业务类需移入正确的包）。
- [x] 2.3 将 `common` 模块下 `com.crediflow.common.utils.IdempotentUtils` 迁移至 `com.crediflow.common.util`。

## 3. 密码学逻辑收敛

- [x] 3.1 在 `common` 模块创建 `com.crediflow.common.util.HmacUtils.java` 并实现 `generateHmacSHA256` 静态方法。
- [x] 3.2 替换 `InternalAuthFilter` 和 `InternalAuthRequestInterceptor` 中重复的 HMAC 实现，使其调用 `HmacUtils`。

## 4. 控制层与持久层隔离 (LoanApplicationController)

- [x] 4.1 在 `loan-application-service` 的服务层中增加缺失的方法（如条件查询分页等），替换 Controller 中的 LambdaQueryWrapper。
- [x] 4.2 封装对外暴露的 Entity 结果对象，使用统一结构化的 Map 或 DTO 进行包装。

## 5. 验证测试

- [x] 5.1 运行 `mvn clean compile -DskipTests` 保证重命名与依赖切换不会打断整体构建流程。
