## ADDED Requirements

### Requirement: 可靠投递与消费者幂等

使用 RocketMQ 传递领域事件时，生产者 MUST 在业务事务提交后再发送消息（或采用事务消息模式）；消费者 MUST 实现幂等处理，重复消息 MUST NOT 导致重复副作用。

#### Scenario: 重复消费不产生二次放款

- **WHEN** 放款完成消息因重投被消费者处理两次
- **THEN** 系统 MUST 仅产生一条放款侧持久化结果且第二次 MUST 识别为重复并跳过

### Requirement: 主题与事件版本

系统 MUST 为主题命名建立约定（领域前缀 + 事件名）；事件体 MUST 包含 `eventId`、`occurredAt` 与 `schemaVersion` 字段。

#### Scenario: 版本不兼容被拒绝

- **WHEN** 消费者收到高于其支持上限的 `schemaVersion`
- **THEN** 消费者 MUST 将消息进入死信或隔离队列并 MUST 记录告警事件

### Requirement: 消息体敏感数据约束

消息体 MUST NOT 携带完整身份证号、完整银行卡号等敏感明文；若必须传递标识 MUST 使用不可逆 token 或脱敏标识。

#### Scenario: 含敏感明文的生产被拒绝

- **WHEN** 生产者尝试发布包含全明文敏感字段的载荷且校验启用
- **THEN** 发送 MUST 失败并 MUST 记录安全告警
