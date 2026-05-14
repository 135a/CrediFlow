# loan-contract-repayment-plan

## Purpose
定义单笔借据生成及还款计划的拆分机制。

## ADDED Requirements

### Requirement: 单笔借据生成
借款风控审批通过后，系统 MUST 为本次借款生成独立的全局唯一借据编号（Loan Receipt/Contract），并记录本金、年化利率和约定期数。
该年化利率 MUST 从配置系统动态读取，MUST NOT 在代码逻辑中硬编码，以支持后续灵活的利率调整。

#### Scenario: 借款终审通过生成借据
- **WHEN** 借款申请状态流转为 APPROVED
- **THEN** 系统 MUST 立即生成一条新的在途借据记录，并处于放款中状态

#### Scenario: 生成借据时读取动态利率
- **WHEN** 系统生成借据并赋值 `AnnualInterestRate` 时
- **THEN** 系统 MUST 读取配置属性（如 `crediflow.loan.rate.annual`）获取利率，若未配置，则回退为默认值 `0.18`

### Requirement: 还款计划实时拆分
生成借据的同时，系统 MUST 依据所选的还款方式（如等额本息或先息后本），将本金及利息拆分到每一期还款计划中。

#### Scenario: 等额本息还款计划拆分
- **WHEN** 用户选择了分 6 期等额本息还款方式
- **THEN** 系统 MUST 生成 6 条针对该借据的期数账单，标明当期应还本金、应还利息和还款到期日
