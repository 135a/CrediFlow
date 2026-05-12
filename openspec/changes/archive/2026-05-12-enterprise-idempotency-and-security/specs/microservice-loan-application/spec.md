## MODIFIED Requirements

### Requirement: 幂等与重复提交防护

同一幂等键下的重复提交 MUST 被拦截并防止多次执行业务逻辑。在借款申请接口上，必须强制校验从前端传入的 `idmpToken`。服务端 MUST 使用基于 Redis 的分布式锁以该 Token 为键加锁，以保证绝对的请求互斥。

#### Scenario: 客户端重试重复提交

- **WHEN** 客户端由于连点或网络超时，使用相同 `idmpToken` 连续多次调用借款申请接口
- **THEN** 系统 MUST 获取到 Redis 分布式锁来处理第一次请求，而对无法获取锁的后续并发请求，系统 MUST 抛出“请勿重复提交申请”的业务异常，且 MUST NOT 创建第二张申请单
