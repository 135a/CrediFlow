## 1. 基础组件与通用模块 (crediflow-common)

- [x] 1.1 引入 Redisson 依赖并封装 `IdempotentUtils`，提供基于 Redis 的加锁/解锁方法。
- [x] 1.2 创建 `@Idempotent` 自定义注解及其对应的 AOP 切面实现，支持通过 Spring EL 提取 `idmpToken`。
- [x] 1.3 编写通用的 Feign 请求拦截器 `InternalAuthRequestInterceptor`，利用预共享秘钥生成 HMAC-SHA256 签名与时间戳 Header。
- [x] 1.4 编写通用的内部安全过滤器 `InternalAuthFilter`，校验 `/api/internal/**` 请求签名，防范非授权调用。

## 2. API 级幂等防重改造

- [x] 2.1 在 `loan-application-service` 的借款申请提交接口方法上添加 `@Idempotent` 拦截，确保前端的高频重发被阻断。
- [x] 2.2 在 `repayment-service` 的主动还款收银接口方法上添加 `@Idempotent` 拦截，避免网络卡顿导致的重复扣款。

## 3. 消费级 MQ 幂等改造 (fund-flow-service)

- [x] 3.1 在 `fund-flow-service` 创建 `cf_mq_idempotent_log` 防重日志表的建表 SQL 以及对应的 Entity/Mapper。
- [x] 3.2 改造 `ContractReadyConsumer` 的放款逻辑：使用 Spring `@Transactional`，在调用放款逻辑前后尝试写入防重记录，利用数据库的唯一索引异常 (`DuplicateKeyException`) 丢弃重复投递的事件。

## 4. 本地消息表机制一致性改造 (loan-application-service)

- [x] 4.1 创建 `cf_local_message` 本地消息表的建表 SQL 以及对应的 Entity/Mapper。
- [x] 4.2 改造借款审批通过 (`approve` 方法) 逻辑：停止直接发送 RocketMQ 消息，改为在同一数据库事务内落库一条 `cf_local_message` (状态: NEW)。
- [x] 4.3 编写一个后台轮询任务（如 `@Scheduled`），定时查询 `cf_local_message` 中未发送的消息，将其投递至 RocketMQ，并在 ACK 返回后更新为 PUBLISHED。
