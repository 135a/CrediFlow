## ADDED Requirements

### Requirement: 指标暴露与最小集合

各服务 MUST 暴露 Prometheus 兼容指标端点（路径与端口可配置）；指标集合至少 MUST 包含：HTTP 请求延迟直方图、错误率、下游调用失败计数、消息消费滞后（若适用）。

#### Scenario: 抓取成功

- **WHEN** Prometheus 按配置抓取 `/metrics`
- **THEN** 端点 MUST 返回 200 且 MUST 不包含敏感标签值（如手机号）

### Requirement: 结构化日志字段

应用日志 MUST 为 JSON 或键值结构化格式；每条日志 MUST 包含 `timestamp`、`level`、`service`、`request_id`（若可得）与 `trace_id`（若可得）。

#### Scenario: 关联 request id

- **WHEN** 请求进入服务且存在 `X-Request-Id`
- **THEN** 该请求生命周期内的日志 MUST 携带相同 request id 字段

### Requirement: 分布式追踪预留

系统 MUST 预留 OpenTelemetry 兼容的 trace 传播入口（ incoming header 解析与 outgoing 注入）；在未启用收集器时 MUST 降级为无额外失败。

#### Scenario: 无 collector 不崩溃

- **WHEN** 未配置 OTLP 导出端点
- **THEN** 应用 MUST 正常启动且 MUST 不产生不可恢复错误循环
