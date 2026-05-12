## ADDED Requirements

### Requirement: 任务清单与调度频率配置

Go 调度服务 MUST 支持至少以下任务类型的配置化调度：自动代扣还款、逾期巡检、罚息计算、到期还款提醒、风控异步任务分发、消息推送调度；每项任务 MUST 具备独立的 cron/间隔配置与启用开关。

#### Scenario: 停用任务不执行

- **WHEN** 某任务在配置中被禁用
- **THEN** 调度器 MUST NOT 触发该任务的执行函数

### Requirement: 分片、重试与防重复执行

长耗时批处理 MUST 支持分片（按用户或借据范围）；失败 MUST 支持有限次重试与退避；同一业务幂等键下任务执行 MUST NOT 产生重复副作用。

#### Scenario: 分片任务部分失败

- **WHEN** 某分片执行失败且仍有重试次数
- **THEN** 系统 MUST 仅重试失败分片并 MUST 记录分片 id 与错误原因

### Requirement: 与 Java 服务交互边界

调度服务 MUST 通过 HTTPS 调用 Java 暴露的任务接口或通过 MQ 投递事件；MUST NOT 直接写入 MySQL 业务库作为权威写入路径（除非经明确设计的只读查询）。

#### Scenario: 代扣触发调用还款接口

- **WHEN** 代扣任务到达执行窗口且选中借据期次
- **THEN** 系统 MUST 调用还款服务的代扣接口并 MUST 使用幂等键
