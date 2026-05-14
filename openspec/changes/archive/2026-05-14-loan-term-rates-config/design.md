## Context
当前 CrediFlow 系统针对借款产品的核心业务参数存在硬编码现象：借款申请接口 `applyLoan` 支持传入任意整数的期数（term）；`loan-contract-service` 中的借据年化利率固定为 18%；`post-loan-service` 中的罚息日利率虽有配置读取但缺乏全局一致的默认值约束。这些硬编码带来了后续业务运营的不便与资产打包的风险。为实现金融信贷产品的标准化，需要将其规范化并提取至应用配置中。

## Goals / Non-Goals

**Goals:**
- 在借款申请入口（`loan-application-service`）实现期数白名单校验，仅允许传入 `3`、`6`、`12` 期。
- 在借据生成环节（`loan-contract-service`），将年化利率从硬编码改造为基于 Spring `@Value` 或 Nacos 的动态配置读取。
- 确保罚息计算环节（`post-loan-service`）具备标准统一的配置键与默认值回退机制。

**Non-Goals:**
- 暂**不实现**“千人千面”的用户差异化定价模型（即不涉及按用户风险等级动态返回不同利率的功能），仅聚焦于全局基础配置的抽取。
- 暂**不开发**供运营人员使用的配置管理可视化管理后台界面（即直接通过修改配置文件或 Nacos 文本实现即可）。

## Decisions

**Decision 1: 借款期数的校验层级**
- **方案选择**：在微服务入口的业务层（如 `LoanApplicationServiceImpl.applyLoan` 或 Controller 处）硬性拦截。如果传入非法的期数，直接抛出 `BusinessException`。
- **依据**：期数枚举在短期内变动频率较低，作为全局性的业务规则应当在流程最早期（发起借款时）被阻断，以避免流转到风控或签约环节后引发状态异常。

**Decision 2: 费率配置的读取方式**
- **方案选择**：继续依托 Spring Boot 的 `@Value` 注解体系，并强制指定默认值。例如：`@Value("${crediflow.loan.rate.annual:0.18}")` 和 `@Value("${crediflow.post-loan.penalty-rate:0.0005}")`。
- **依据**：这种方式既兼容现有单体开发时的 `application.yml` 测试，又能零成本平滑对接生产环境的 Nacos 动态配置刷新机制，开发成本最低且最稳定。

## Risks / Trade-offs

**Risks:** 
- 修改入参约束为白名单后（仅支持 3/6/12 期），可能导致前端或现有的单元测试/自动化测试脚本因为使用其他期数（如 `term=1` 或 `term=24`）而大面积报错（BREAKING CHANGE）。
  - **Mitigation**：开发完成后，需要同步排查 `test` 目录下的用例，并将涉及到 `applyLoan` 的所有 `term` 入参更新为合法的枚举值。

- 费率配置缺失风险。
  - **Mitigation**：利用 Spring 属性默认值的语法 `:${default}` 进行兜底，确保即使未下发配置项，系统也能正常以 18% 和 万分之五 运转。
