## ADDED Requirements

### Requirement: 统一网关鉴权与身份透传
网关 MUST 拦截所有向下的受保护流量，并在网关层完成 JWT 令牌的合法性与时效性校验；校验通过后，网关 MUST 解析出对应的身份信息，并通过特定的 HTTP Header 向下层微服务透传。

#### Scenario: 拦截未携带有效令牌的请求
- **WHEN** 外部客户端尝试直接请求 `/api/app/credit/apply` 接口而不携带有效 JWT
- **THEN** 网关 MUST 在路由分发前直接拦截请求并返回 401 Unauthorized

#### Scenario: 身份信息透传
- **WHEN** 带有有效 JWT 的请求到达网关
- **THEN** 网关验证成功后，MUST 将 Token 内置的 `userId` 抽取出来，以 `X-User-Id` 的 Header 形式附加到原请求中，然后再反向代理至后端的微服务集群
