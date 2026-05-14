## Why
目前系统中借款期数（Term）支持任意整数，且借款年化利率与罚息均在代码中硬编码，这不符合真实金融业务场景中资产打包标准化和合规灵活调配的要求。为了方便运营和产品人员根据市场、风险策略动态调整借款方案与费率，我们需要将期数限制为标准枚举，并将利率相关参数改为支持默认值的配置读取模式。

## What Changes
- 将借款支持的分期期数限定为枚举值：`3`（短期周转）、`6`（中期消费）、`12`（大额/年度分期）。
- 修改借据生成逻辑，将借款年化利率（原硬编码为 18%）改为从全局或服务配置中读取，并提供默认值 `0.18`。
- 确认与规范贷后服务中罚息日利率的配置读取逻辑，确保统一且带有合理的默认值（如 `0.0005`）。
- **BREAKING**: `loan-application-service` 的借款申请 API 及后续链路将严格校验期数入参，传入非 3、6、12 的任意其他整数将被强行拦截并返回错误。

## Capabilities

### New Capabilities
- `loan-product-configuration`: 借款产品核心参数化配置，定义支持的分期期数枚举列表以及基础年化利率的动态读取机制。

### Modified Capabilities
- `microservice-loan-application`: 修改借款申请的业务规则，在核心入口拦截不支持的分期期数。
- `loan-contract-repayment-plan`: 借据合同生成时，剥离硬编码的年化利率，适配为从配置系统注入。

## Impact
- **相关代码**: 
  - `LoanApplicationServiceImpl.java` 需要新增基于枚举的 `term` 参数校验。
  - `LoanContractServiceImpl.java` 需要引入 `@Value` 或配置类读取年化利率，并在生成 `LoanReceipt` 时赋值。
- **API 约定**: `/api/app/loan-application/apply` 接口的 `term` 参数现在具有明确的业务约束，前后端约定变更。
- **系统配置**: 服务需要在 `application.yml` 或 Nacos 中新增/规范 `crediflow.loan.terms.allowed` 及 `crediflow.loan.rate.annual` 等配置项。
