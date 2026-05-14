## ADDED Requirements

### Requirement: 单笔借据生成
借款风控审批通过后，系统 MUST 为本次借款生成独立的全局唯一借据编号（Loan Receipt/Contract），并记录本金、年化利率和约定期数。

#### Scenario: 借款终审通过生成借据
- **WHEN** 借款申请状态流转为 APPROVED
- **THEN** 系统 MUST 立即生成一条新的在途借据记录，并处于放款中状态

### Requirement: 还款计划实时拆分
生成借据的同时，系统 MUST 依据所选的还款方式（如等额本息或先息后本），将本金及利息拆分到每一期还款计划中。

#### Scenario: 等额本息还款计划拆分
- **WHEN** 用户选择了分 6 期等额本息还款方式
- **THEN** 系统 MUST 生成 6 条针对该借据的期数账单，标明当期应还本金、应还利息和还款到期日
