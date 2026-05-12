## Purpose

TBD

## Requirements

### Requirement: 同步调用契约与超时

Java 微服务间通过 OpenFeign（或等价 HTTP 客户端）进行同步调用时，MUST 为每个下游依赖配置连接超时与读超时；超时错误 MUST 映射为可观测的错误码且不泄露内部栈信息给外部客户端。

#### Scenario: 下游超时

- **WHEN** 下游服务在配置读超时内未返回
- **THEN** 调用方 MUST 触发熔断/降级策略（若已配置）且 MUST 记录包含 request id 的错误日志

### Requirement: 错误语义与重试边界

同步调用 MUST NOT 对非幂等写操作默认自动重试；对幂等读操作允许有限次重试且 MUST 有退避。

#### Scenario: 非幂等写禁止盲重试

- **WHEN** Feign 客户端对标注为非幂等的写接口发生网络超时
- **THEN** 客户端 MUST NOT 自动重试，且上层业务 MUST 使用幂等键显式重新发起写请求

### Requirement: 服务间身份传递

同步调用 MUST 透传或重建与网关一致的 request id；MUST 传递调用链所需的最小认证上下文（禁止在日志中输出令牌明文）。

#### Scenario: request id 贯通

- **WHEN** 上游请求包含 `X-Request-Id`
- **THEN** 下游调用 MUST 携带相同 `X-Request-Id` 值
