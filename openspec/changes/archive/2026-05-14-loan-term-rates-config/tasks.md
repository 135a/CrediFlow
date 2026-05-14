## 1. 借款申请服务改造 (loan-application-service)

- [x] 1.1 修改 `LoanApplicationServiceImpl.java` 的 `applyLoan` 方法，在业务逻辑入口处增加对 `term` 入参的枚举白名单校验（仅允许 3、6、12）。
- [x] 1.2 当校验不通过时，统一抛出带有明确提示的 `BusinessException`（如“不支持的分期期数”）。

## 2. 借据合同服务改造 (loan-contract-service)

- [x] 2.1 在 `loan-contract-service` 的 `application.yml`（或 Nacos 统一配置）中增加基础年化利率的配置项，例如 `crediflow.loan.rate.annual: 0.18`。
- [x] 2.2 修改 `LoanContractServiceImpl.java`，通过 `@Value("${crediflow.loan.rate.annual:0.18}")` 注入年化利率属性（支持 String 或 BigDecimal）。
- [x] 2.3 修改 `generateReceiptAndPlan` 方法，将原本硬编码的 `new BigDecimal("0.18")` 替换为从配置读取的动态年化利率。

## 3. 贷后与罚息服务改造 (post-loan-service)

- [x] 3.1 检查 `PostLoanServiceImpl.java`，确认其 `@Value` 配置（如 `crediflow.post-loan.penalty-rate`）具有合理的默认值回退 `0.0005`。
- [x] 3.2 确保 `post-loan-service` 的 `application.yml` 中包含明确的该项全局配置定义，以保持系统的可维护性与全局统一。
