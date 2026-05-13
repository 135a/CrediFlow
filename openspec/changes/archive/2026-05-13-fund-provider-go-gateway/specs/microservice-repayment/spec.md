# microservice-repayment（Delta）

## MODIFIED Requirements

### Requirement: 主动还款与幂等

系统 MUST 支持主动还款；主动还款请求接口 MUST 强制要求传入从前置接口获取的 `idmpToken`。服务端 MUST 基于该 Token 与 Redis 锁机制实现并发防重，确保重复请求 MUST NOT 多次触发资金侧扣款。真实对资金方的还款/代扣 HTTP 调用 MUST 由 Go 资金网关执行；获得锁的线程 MUST 通过内网调用网关受理接口发起扣款，MUST NOT 在 Java 内直连资金方。

#### Scenario: 重复还款请求

- **WHEN** 用户在网络卡顿时连续点击「确认还款」按钮，导致附带同一个 `idmpToken` 的多次还款请求到达网关
- **THEN** 系统 MUST 仅在第一个拿到 Redis 锁的线程中向 Go 资金网关发起一笔有效代扣/还款受理，后续抢锁失败的请求 MUST 抛出「请勿重复支付」异常并直接丢弃，绝不产生多笔扣款

### Requirement: 自动生成还款计划与主动还款

系统 MUST 在收到放款成功的事件后，立刻根据用户选择的期数、本金和当前利率，为其生成包含每一期应还本息总额的还款计划表，并提供主动还款接口。

#### Scenario: 成功生成还款计划

- **WHEN** 接收到 `FUND_DISBURSED_EVENT` 消息
- **THEN** 系统 MUST 为借款单计算完整的等额本息/等额本金还款计划明细并落库，同时将借款单状态置为「还款中」

#### Scenario: 用户提前主动还款

- **WHEN** 用户通过 APP 针对某一期或全款发起主动还款操作
- **THEN** 系统 MUST 校验应还金额，通过内网调用 Go 资金网关完成还款/代扣受理；在收到网关桥接的成功终态后，核销对应还款计划期次并更新为「已结清」状态
