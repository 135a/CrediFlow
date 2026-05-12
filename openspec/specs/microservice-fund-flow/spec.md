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

系统 MUST 为第三方支付回调与订单状态预留接口与表结构扩展点；未启用第三方支付时 MUST 明确返回未开通语义。

#### Scenario: 回调验签失败

- **WHEN** 第三方支付回调签名验证失败
- **THEN** 系统 MUST 拒绝处理并 MUST 记录安全审计事件
