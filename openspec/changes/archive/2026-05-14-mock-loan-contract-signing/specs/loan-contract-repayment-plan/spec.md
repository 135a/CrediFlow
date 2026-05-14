## MODIFIED Requirements

### Requirement: 单笔借据生成
借款合同流转完毕（变为 SIGNED 状态）后，系统 MUST 为本次借款生成独立的全局唯一借据编号（Loan Receipt/Contract），并记录本金、年化利率和约定期数。
该年化利率 MUST 从配置系统动态读取，MUST NOT 在代码逻辑中硬编码，以支持后续灵活的利率调整。

#### Scenario: 合同签署完毕生成借据
- **WHEN** 关联的 `cf_loan_contract` 状态流转为 `SIGNED` 并准备放款
- **THEN** 系统 MUST 立即生成一条新的在途借据记录，并处于放款中状态

#### Scenario: 生成借据时读取动态利率
- **WHEN** 系统生成借据并赋值 `AnnualInterestRate` 时
- **THEN** 系统 MUST 读取配置属性（如 `crediflow.loan.rate.annual`）获取利率，若未配置，则回退为默认值 `0.18`
