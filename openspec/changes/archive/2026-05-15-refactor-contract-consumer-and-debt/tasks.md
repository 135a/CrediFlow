## 1. 消费者幂等与边界重构

- [x] 1.1 在 `LoanContractServiceImpl.java` 的 `generateContract` 方法实现中补充前置的防重查询逻辑：根据 `applicationId` 和 `userId` 检索 `cf_loan_contract`，如果已存在则提前 return（或返回已有实例）。
- [x] 1.2 修改 MQ 消费者端点 `LoanApprovedConsumer.java`：仅保留对 `generateContract` 的调用逻辑，彻底剔除随后附加的 `generateReceiptAndPlan`（生成借据计划）以及 `creditClient.deductQuota`（扣减远端额度）的操作，将其降级为只做状态草案初始化。
- [x] 1.3 核对本变更涉及的实体（如 `LoanContract`、`LoanReceipt`、`RepaymentPlan` 等）的 `@TableId(type = IdType.ASSIGN_ID)`（或项目等价配置）与 `design.md` 中「决议 3」实体策略表一致，满足分片与跨服务引用。

## 2. 合同签署防重与统一入口

- [x] 2.1 在 `LoanContractServiceImpl.java` 中梳理真正的用户签署入口（如 `signContract` 方法），确保在合同状态成功更新为 `SIGNED` 之后，将之前消费者中被剔除的 `generateReceiptAndPlan` 和远端额度扣除逻辑填补并串联在此处执行。

## 3. 代码规整与异常治理

- [x] 3.1 全局扫描 `loan-contract-service`，把代码里抛出的野生 `RuntimeException("必须同意协议才能签约")` 或其他异常直接替换为标准的 `throw new BusinessException(ErrorCode.BUSINESS_ERROR, "...")`。
- [x] 3.2 对 `LoanContractServiceImpl.java` 中遗留的烂代码进行清理：删掉代码里遍地开花的 `java.math.BigDecimal`、`java.util.HashMap` 等全限定类名，在文件顶部正经使用 `import` 关键字。
- [x] 3.3 检查工程下的配置文件（如 `application.yml` 等），删除无人引用的悬空配置 `crediflow.contract.interest-rate` 及其引用。

## 4. 最终验证

- [x] 4.1 运行全局 `mvn clean compile -DskipTests` 验证没有破坏编译树和抛出未知异常。
