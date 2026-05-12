## ADDED Requirements

### Requirement: 放款与出金流水对接
系统 MUST 监听合同就绪事件触发真实的资金划转（通过 Mock 支付网关），并记录详细的资金流向与账务凭证。

#### Scenario: 触发自动放款
- **WHEN** 接收到 `CONTRACT_READY_EVENT` 消息
- **THEN** 系统 MUST 调用第三方放款接口将资金打入用户账户，成功后 MUST 落库一条放款成功流水，并向 MQ 投递 `FUND_DISBURSED_EVENT` 消息
