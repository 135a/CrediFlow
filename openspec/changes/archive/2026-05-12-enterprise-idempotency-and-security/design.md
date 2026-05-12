## Context

在 CrediFlow 系统的近期演进中，我们完成了核心生命周期的全链路打通，引入了 RocketMQ 事件驱动和 Go 分布式调度系统。然而，在金融级生产环境中，微服务在面临网络超时、重试风暴、黑客直接攻击内网时，会暴露一致性和安全性弱点。为了解决“重复生成借款单”、“放款回调重复打款”、“跨服务被伪造请求”的问题，本设计提案负责给出企业级的幂等性与零信任微服务通信的技术落地方案。

## Goals / Non-Goals

**Goals:**
- 提供一种通用的 API 幂等防重机制（基于 Token），适配所有核心的交易写接口。
- 为基于 RocketMQ 的事件消费提供标准的幂等控制模式。
- 引入本地消息表（Local Message Table），杜绝由于 MQ 宕机或网络异常导致的跨服务事务数据不一致。
- 构建微服务间的零信任（Zero-Trust）安全通信机制，通过拦截器实现 Feign 调用的强制签权。

**Non-Goals:**
- 不涉及从头构建一个分布式事务框架（如 Seata 的 AT/TCC 模式），本期坚持以“最终一致性”和防重为核心。
- 不干涉现有的 APISIX 对外部客户端的 JWT 验证机制。

## Decisions

### Decision 1: 分布式锁与 Token 幂等机制 (API层)
**设计说明**：在核心写操作（借款申请、还款请求）前，前端或 APP 需先获取全局唯一的 `idmpToken`。在业务入口使用 AOP（如 `@Idempotent` 注解）配合 Redisson 分布式锁机制。锁的 Key 就是 `IDMP:TOKEN:{token}`。
**Rationale (原因)**：Redisson 的锁机制自带可重入与看门狗特性，能够有效应对高并发场景下的表单重复提交。如果获取不到锁，说明请求正在处理中，直接返回业务级错误；如果获取到锁，先在数据库/Redis 查验是否已存在业务结果。

### Decision 2: 本地消息表实现最终一致性 (发送层)
**设计说明**：为了防止向 MQ 发送消息时发生异常（发完消息后落库失败，或者落库成功发消息超时），我们将使用本地消息表机制。在发出核心事件（如“借款已通过”）时，将业务数据更新（借款状态变为 CONTRACT_PROCESSING）与插入消息记录（`cf_local_message`，状态设为 NEW）放在**同一个本地数据库事务**中完成。
随后，由定时任务或消息发送后置处理器异步读取状态为 NEW 的消息投递至 RocketMQ Broker，成功后更新状态为 PUBLISHED。
**Rationale**：最大程度保证业务落盘与消息发送的原子性。

### Decision 3: 基于数据库的幂等日志表 (消费层)
**设计说明**：由于 RocketMQ 仅保证 At Least Once（至少一次投递），消费者端（如 `microservice-fund-flow` 的放款模块）必须自行处理重投递。我们将引入统一的 `cf_mq_idempotent_log` 防重表，主键（或唯一索引）为消息 ID 结合业务流向。在消费时，通过 Spring 事务将处理业务与写入该表包裹在一起，利用数据库的唯一主键冲突异常（`DuplicateKeyException`）实现完美的幂等。

### Decision 4: 基于预共享秘钥的 HMAC 微服务签名体系 (安全层)
**设计说明**：在所有的内部 OpenFeign 调用中，注入一个通用的 RequestInterceptor。该拦截器根据共享秘钥（`crediflow.internal.secret`）和当前时间戳生成 HMAC-SHA256 签名，并将其连同时间戳放入 `X-Internal-Sign` 与 `X-Timestamp` Header 中。
在各个微服务的 Web Filter 中，拦截带有 `/api/internal/**` 前缀的请求，校验时间戳（防重放，例如超过 5 分钟的拒绝）和签名。
**Rationale**：这是目前微服务间建立“内部防御圈”最轻量且安全的方式。

## Risks / Trade-offs

- **Risk: Redis 的强依赖**
  - **Mitigation**: API 防重机制强依赖 Redis 的高可用，需要将系统级 Redis 设置为集群模式；如果 Redis 异常，可配置降级策略转用 DB 唯一索引。
- **Risk: 本地消息表的写放大**
  - **Mitigation**: 每产生一条核心业务流转就会增加一次对 `cf_local_message` 的写入。对于我们的小额信贷业务，并发在几百 QPS，写放大完全可以接受，且我们设定仅针对跨微服务的“生命周期转换核心事件”使用此模式。
- **Risk: Feign 调用的额外计算开销**
  - **Mitigation**: HMAC-SHA256 的计算性能极快，在纳秒级，几乎不对微服务间吞吐量构成实质影响。 
