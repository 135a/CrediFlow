## 1. 实体层修正

- [x] 1.1 将 `UserCreditQuota.java` 中的 `@TableId(type = IdType.AUTO)` 修改为 `@TableId(type = IdType.ASSIGN_ID)`。
- [x] 1.2 将 `CreditReviewQueue.java` 中的 `@TableId(type = IdType.AUTO)` 修改为 `@TableId(type = IdType.ASSIGN_ID)`。

## 2. 接口与服务层重构

- [x] 2.1 在 `CreditApplicationService.java` 中增加方法声明 `Map<String, Object> getLastApplicationStatus(Long userId)`，并在其实现类中将原来 `CreditInternalController.getCreditStatusInternal` 的查询组装逻辑迁入。
- [x] 2.2 在 `CreditApplicationService.java` 中增加方法声明 `Map<String, Object> getLastApplicationResult(Long userId)`，并在其实现类中将原来 `CreditInternalController.getLastResultInternal` 的查询组装逻辑迁入。
- [x] 2.3 在 `CreditService.java` 中增加方法声明 `Map<String, Object> getQuotaSummary(Long userId)`，并在其实现类中将原来 `CreditInternalController.getCreditQuotaInternal` 的查询组装逻辑迁入。
- [x] 2.4 在 `CreditService.java` 中增加方法声明 `void escalateRiskSignal(Map<String, Object> signalData)` 和 `String evaluateLoanRisk(Map<String, Object> req)` 以及 `void enqueueLoanReview(Map<String, Object> req)`，并在实现类中完成对逻辑的承接。
- [x] 2.5 在 `CreditApplicationService.java` 中增加支持 Admin 分页查询及检索的方法，以接管 `CreditAdminController.listApplications` 和 `CreditAdminController.listReviewQueue` 的查询逻辑。

## 3. 控制层瘦身

- [x] 3.1 改造 `CreditInternalController`，将内部所有的 `LambdaQueryWrapper` 操作及其对于 `Mapper` 的直接依赖移除，全面替换为调用上述 Service 提供的新方法。
- [x] 3.2 改造 `CreditAdminController`，将内部的分页构建、条件判断等 DAO 相关逻辑彻底移除，全面替换为调用 Service 层。

## 4. 验证测试

- [x] 4.1 运行 `mvn clean compile -DskipTests`，确保全部重构后的类路径和调用均通过编译校验。
