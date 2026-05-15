## 1. Credit 路由安全重构

- [x] 1.1 在 `credit-risk-service` 中创建 `CreditInternalController.java`，配置 `@RequestMapping("/api/internal/credit")` 类级注解。
- [x] 1.2 将 `CreditController` 中原有的内部接口（所有以 `/internal/` 开头的方法，如 active、apply、status、quota、last-result、risk-signal/escalate、evaluate-loan、review/enqueue、quota/deduct）剪切至 `CreditInternalController` 中，并去除各自的 `/internal` 路径前缀以匹配新的安全路由结构。
- [x] 1.3 修改所有微服务（`credit-risk-service` 本身及其他如 `loan-contract-service`, `loan-application-service`, `app-bff-service` 等）内部调用的 `CreditClient` Feign 接口，将原先错误的 `/api/app/credit/internal/...` 路径更新为 `/api/internal/credit/...`。

## 2. 合同服务数据库修补

- [x] 2.1 修改 `loan-contract-service` 下现有的 `V1__init_loan_contract.sql`，在 `cf_loan_contract` 表语句中追加 `contract_type VARCHAR(32) NOT NULL DEFAULT 'LOAN_CONTRACT'`。
- [x] 2.2 修改上述 `V1` 脚本，将 `cf_loan_contract` 表 `status` 列的默认值由 `DEFAULT 'GENERATED'` 改为 `DEFAULT 'INIT'`。
- [x] 2.3 在 `db/migration` 下创建 `V2__init_loan_receipt_and_repayment_plan.sql`，并加入完整的 `cf_loan_receipt`（借据表）和 `cf_repayment_plan`（还款计划表）的 CREATE TABLE 脚本。

## 3. 幽灵代码清理

- [x] 3.1 删除 `loan-contract-service` 中的无用代码 `PdfGeneratorUtil.java`。
- [x] 3.2 删除 `loan-contract-service` 中 `resources/templates/contract_template.txt` 等无用的本地模板（如存在）。

## 4. 编译校验

- [x] 4.1 运行全局 `mvn clean compile -DskipTests` 确保代码编译链无死角报错。
