## Why

当前 CrediFlow 系统的业务链路已经跑通，但在极端并发场景、网络超时重试或恶意攻击下，系统存在明显的脆弱性。例如，用户连点可能导致重复生成借款单，MQ 消息重试可能导致给用户打两次款，内部 Feign 接口也可能被绕过网关直接攻击。为了达到企业级和金融级的标准，我们必须对全链路实施严格的**幂等性、最终一致性与内部安全性加固**。

## What Changes

本次变更将系统提升至金融级强健性：
- **分布式防重与幂等（API层）**：在核心业务接口（借款申请、主动还款）强制引入 `idmpToken`，利用 Redis + Redisson 分布式锁实现绝对防重放，拦截高并发的刷单攻击。
- **MQ 幂等消费（消费层）**：为 RocketMQ 的 Consumer 增加幂等表机制（或依赖 DB 唯一索引），确保像“出金放款”这样的动作即使收到多条相同的 MQ 消息也仅会执行一次。
- **内部通信鉴权（安全层）**：引入 `Internal-Auth` 秘钥机制。所有的 Feign 调用将自动携带带有时间戳与签名的 HTTP Header，下游服务强校验该 Header，杜绝通过内网直接绕过 APISIX 网关访问后端微服务的可能。
- **本地消息表（一致性增强）**：为防范服务发出 MQ 消息前宕机，引入本地消息表模式，先落库业务与消息日志在同一事务，再由定时任务或直接发送至 MQ，确保 100% 投递成功。

## Capabilities

### New Capabilities
- `enterprise-idempotency-control`: 跨服务全局防重防刷与 API / MQ 级别幂等性控制。
- `internal-api-security`: 微服务间东西向流量的安全认证与防越权调用体系。

### Modified Capabilities
- `microservice-loan-application`: 申请借款接口必须拦截相同的 `idmpToken`。
- `microservice-repayment`: 主动还款接口必须拦截相同的 `idmpToken`。
- `microservice-fund-flow`: 放款 MQ 消费者必须防范同一借款单重复放款。
- `integration-feign-http`: 所有的微服务间 Feign 交互需增加 `X-Internal-Sign` 校验拦截器。

## Impact

- **API 契约**：所有涉及写操作的核心前端接口必须在请求头或 Body 中携带由前端预先申请的 `idmpToken`。
- **数据库设计**：需要增加通用的 `sys_idempotent_log` 防重表及 `sys_local_message` 本地消息表。
- **性能评估**：Redis 分布式锁的加入会略微增加核心接口的 RT（响应时间），但由于并发粒度为用户/订单级，整体吞吐量影响可控。
