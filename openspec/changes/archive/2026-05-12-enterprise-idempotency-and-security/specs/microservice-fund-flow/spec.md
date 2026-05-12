## MODIFIED Requirements

### Requirement: 放款与出金流水对接

系统 MUST 监听合同就绪事件触发真实的资金划转（通过 Mock 支付网关），并记录详细的资金流向与账务凭证。对于 MQ 的消费者，系统 MUST 建立消息防重机制（如业务库 Unique Key 或通用的防重日志表），保障该出金事件在 RocketMQ Broker 多次投递（At Least Once）的情况下也 MUST NOT 造成重复放款。

#### Scenario: 触发自动放款

- **WHEN** 接收到 `CONTRACT_READY_EVENT` 消息
- **THEN** 系统 MUST 在执行放款前校验该 `application_id` 是否已经被处理放款过。如果尚未处理，则调用第三方放款接口将资金打入用户账户，并在同一个数据库事务中记录防重日志与放款流水；如果已经处理过，则直接返回 ACK (Success)

#### Scenario: RocketMQ 重复投递出金事件

- **WHEN** 由于网络原因或 Broker 故障，RocketMQ 将同一个 `CONTRACT_READY_EVENT` 投递了第二次
- **THEN** 系统查询防重表（或命中唯一主键冲突），识别出该单据已放款，MUST 不再调用第三方网关，而是将此 MQ 消息直接返回消费成功
