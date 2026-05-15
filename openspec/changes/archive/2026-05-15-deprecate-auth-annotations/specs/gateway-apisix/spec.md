## ADDED Requirements

### Requirement: 网关作为 JWT 鉴权与免登规则的唯一权威

在彻底废弃 `@IgnoreAuth` 后，网关（APISIX）MUST 作为判定一个外部接口是否需要强制校验 JWT 的**唯一决策层**。下游微服务 MUST 默认信任网关转发过来的请求的身份状态（即如果有 `X-User-Id` 头即已登录，如果没有则为未登录），业务应用层 MUST NOT 试图通过任何代码注解机制（如 `@IgnoreAuth`）自行绕过或补充拦截器的验证逻辑。

#### Scenario: 未登录访问公开 API
- **WHEN** 客户端访问已被 APISIX 路由规则列入免密白名单（例如获取系统配置或发送短信验证码）的接口时
- **THEN** APISIX MUST 不执行 JWT 校验，直接将请求透传给下游，下游服务正常响应且 MUST NOT 抛出未鉴权异常。
