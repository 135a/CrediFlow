## ADDED Requirements

### Requirement: 全局防重放与分布式幂等

系统 MUST 在需要防止表单重复提交和防止资金重扣的核心写操作接口上提供 `idmpToken` 的校验机制。客户端在调用前 MUST 从服务端获取全局唯一的 Token，在提交时附带。服务端 MUST 基于该 Token 与 Redis 锁保障同一业务操作只会被处理一次。

#### Scenario: 客户端重复点击提交按钮
- **WHEN** 客户端由于网络卡顿或异常操作，携带同一个 `idmpToken` 连续发起两次核心写请求
- **THEN** 系统 MUST 借助 Redisson 分布式锁拦截第二次请求，并返回“请勿重复提交”的提示或直接返回第一次请求的结果（不产生第二次业务逻辑）

### Requirement: 事务级本地消息表确保消息最终一致性

为了防止服务发送 RocketMQ 消息失败（如 Broker 宕机、网络超时），核心事件 MUST 采用本地消息表模式，即在同一个数据库事务中保存业务状态和事件投递记录，再由定时任务或事务后置钩子推送到 MQ。

#### Scenario: 消息 Broker 短暂不可用
- **WHEN** 借款审批通过，需要发送 `LOAN_APPROVED_EVENT` 时，RocketMQ 服务不可用
- **THEN** 系统 MUST 将事件落入本地 `sys_local_message` 表，随后后台定时轮询任务 MUST 在 Broker 恢复后重发该消息，保证 100% 成功送达
