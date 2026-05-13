# scheduler-go（Delta）

## MODIFIED Requirements

### Requirement: Go 语言分布式高并发调度

系统 MUST 定时调度业务服务接口，驱动贷款全生命周期的延迟与周期性任务运转。

#### Scenario: 每日自动代扣还款调度

- **WHEN** 到达每天预设的自动代扣时间点（如早上 8 点）
- **THEN** Go 调度服务（`batch-service`）MUST 批量查询当天应还款的用户列表，并通过内网并发调用 Go 资金网关的到期代扣受理接口，由网关完成对资金方的加签、加密、HTTPS 调用、重试与熔断；MUST NOT 直接调用 Java `repayment-service` 的内部扣款接口作为资金外呼出口

#### Scenario: 每日零点触发逾期巡检

- **WHEN** 每天午夜 00:01
- **THEN** Go 调度服务 MUST 调用贷后管理系统的巡检接口，触发状态降级与罚息生成逻辑
