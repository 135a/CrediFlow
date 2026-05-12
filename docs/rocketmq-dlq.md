# RocketMQ 死信与重试（占位）

- 消费失败重试次数与退避由 `rocketmq-spring` / Broker 订阅级配置统一约束。
- 超过重试上限的消息进入 **DLQ 主题**（命名建议：`%DLQ%` + `consumerGroup`），需配套告警与人工排查流程。
- 与 `cf_mq_consumer_processed` 幂等表配合：业务处理前 `INSERT IGNORE` 成功后再处理；处理中异常可依赖重试，终态失败写入差错表（后续任务扩展）。
