## Purpose

TBD
## Requirements
### Requirement: 资金流水记录

资金流水服务 MUST 记录放款与还款相关流水条目；每条流水 MUST 包含借据号、方向、金额、币种、业务单号、幂等键与时间戳。

#### Scenario: 放款成功记账

- **WHEN** 放款完成事件到达且幂等键未处理过
- **THEN** 系统 MUST 创建一条放款成功流水且 MUST 保证可查询对账

### Requirement: 对账校验

系统 MUST 提供按日期/渠道汇总的对账查询能力；发现差额时 MUST 产生可对账的差异记录状态。

#### Scenario: 汇总不平

- **WHEN** 对账任务发现总账与明细汇总不一致
- **THEN** 系统 MUST 标记差异并 MUST 记录任务批次 id

### Requirement: 第三方支付对接预留

系统 MUST 为资金方经 Go 资金网关异步回调后的订单状态同步预留接口与表结构扩展点；对外资金方验签与原始回调报文处理 MUST 在 Go 网关完成，Java 侧 MUST 仅消费网关桥接后的结构化结果或领域事件。未启用资金对接时 MUST 明确返回未开通语义。

#### Scenario: 回调验签失败

- **WHEN** Go 网关或 Java 在消费桥接回调/事件时签名验证失败（含被篡改载荷）
- **THEN** 系统 MUST 拒绝处理并 MUST 记录安全审计事件

### Requirement: 放款与出金流水对接

系统 MUST 监听合同就绪事件触发真实资金划转；对与持牌资金方的出金 HTTP 调用 MUST 全部由 Go 资金网关执行，Java `fund-flow-service` MUST NOT 直连资金方或代扣通道。资金 MUST 自资金方对公账户直接划入用户绑定储蓄卡；平台自有账户 MUST NOT 作为过路账户。对于 MQ 的消费者，系统 MUST 建立消息防重机制（如业务库 Unique Key 或通用的防重日志表），保障该出金事件在 RocketMQ Broker 多次投递（At Least Once）的情况下也 MUST NOT 造成重复放款。

#### Scenario: 触发自动放款

- **WHEN** 接收到 `CONTRACT_READY_EVENT` 消息
- **THEN** 系统 MUST 在执行放款前校验该 `application_id` 是否已经被处理放款过。如果尚未处理，则 MUST 调用内部 Go 资金网关的放款受理接口（由网关完成加签、加密、HTTPS 调用与渠道幂等），并在同一个数据库事务中记录防重日志与放款流水；如果已经处理过，则直接返回 ACK (Success)

#### Scenario: RocketMQ 重复投递出金事件

- **WHEN** 由于网络原因或 Broker 故障，RocketMQ 将同一个 `CONTRACT_READY_EVENT` 投递了第二次
- **THEN** 系统查询防重表（或命中唯一主键冲突），识别出该单据已放款，MUST 不再调用 Go 资金网关重复受理，而是将此 MQ 消息直接返回消费成功

### Requirement: 资金流水与资金方标识

资金流水服务 MUST 在放款与还款流水中记录资金方标识（`providerId` 或等价字段）、网关注册请求号/资金方流水号（若可得）及回调报文摘要引用，以支持多家资金方对账与司法审计。

#### Scenario: 放款成功流水含渠道维度

- **WHEN** 放款成功事件到达且幂等键未处理过
- **THEN** 系统 MUST 创建放款成功流水且 MUST 包含资金方标识与可关联到网关/资金方的外部流水引用，并 MUST 保证可查询对账

