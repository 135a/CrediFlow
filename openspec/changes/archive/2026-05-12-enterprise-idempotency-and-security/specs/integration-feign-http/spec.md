## MODIFIED Requirements

### Requirement: 服务间身份传递

同步调用 MUST 透传或重建与网关一致的 request id；MUST 传递调用链所需的最小认证上下文（如 `X-User-Id`）。此外，服务间的同步调用 MUST 在 HTTP Header 中增加内部安全签名（如 `Internal-Auth`），该签名由预共享秘钥与时间戳构成，以防止微服务间被未经授权的非法内网流量访问。

#### Scenario: request id 贯通

- **WHEN** 上游请求包含 `X-Request-Id`
- **THEN** 下游调用 MUST 携带相同 `X-Request-Id` 值

#### Scenario: 内部 Feign 调用携带签名验证

- **WHEN** Java 微服务通过 OpenFeign 向另一微服务发起 HTTP 请求
- **THEN** 发起方 MUST 通过 RequestInterceptor 自动注入包含签名的 Header，接收方的 Filter 拦截器 MUST 验证该签名通过后才允许放行
