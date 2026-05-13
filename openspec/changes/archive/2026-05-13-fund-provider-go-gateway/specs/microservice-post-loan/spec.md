# microservice-post-loan（Delta）

## ADDED Requirements

### Requirement: 放款与还款终态驱动履约与画像

贷后服务 MUST 订阅或接收由 Go 资金网关桥接的放款成功/失败与还款结清终态事件（与 `FUND_DISBURSED_EVENT`、`REPAYMENT_SETTLED_EVENT` 等对齐）；在终态到达后 MUST 更新用户还款履约相关视图或 outbound 数据，供风控画像与策略使用。处理 MUST 幂等：同一资金方回调幂等键重复投递 MUST NOT 重复写入矛盾状态。

#### Scenario: 还款结清更新履约数据

- **WHEN** 网关桥接的还款成功终态到达且幂等键未处理过
- **THEN** 系统 MUST 更新该借据/期次的结清信息并 MUST 产生可追溯的审计记录，且 MUST 向画像更新通道投递履约标签更新（具体字段由集成任务实现）

#### Scenario: 放款失败不进入错误结清路径

- **WHEN** 网关桥接放款失败终态（含明确拒绝原因）
- **THEN** 系统 MUST NOT 将借据标记为「还款中」；MUST 记录失败原因并 MUST 保持与借款申请/合同状态机一致的终态语义
