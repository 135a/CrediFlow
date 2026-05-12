## MODIFIED Requirements

### Requirement: 主动还款与幂等

系统 MUST 支持主动还款；主动还款请求接口 MUST 强制要求传入从前置接口获取的 `idmpToken`。服务端 MUST 基于该 Token 与 Redis 锁机制实现并发防重，确保重复请求 MUST NOT 多次扣除用户的资金。

#### Scenario: 重复还款请求

- **WHEN** 用户在网络卡顿时连续点击“确认还款”按钮，导致附带同一个 `idmpToken` 的多次还款请求到达网关
- **THEN** 系统 MUST 仅在第一个拿到 Redis 锁的线程中发起真实的收银代扣，后续抢锁失败的请求 MUST 抛出“请勿重复支付”异常并直接丢弃，绝不产生多笔扣款
