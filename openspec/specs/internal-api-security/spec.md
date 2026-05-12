## Purpose

TBD

## Requirements

### Requirement: 微服务内网零信任防越权调用

为了防止恶意用户绕过 APISIX 网关，通过内网直接请求后端的 HTTP 服务，系统 MUST 对内部所有的 Feign 调用附加自动安全签名（包含防重放时间戳与预共享密钥哈希）。服务端接收请求时 MUST 拦截并验证签名。

#### Scenario: 黑客伪造内网请求
- **WHEN** 攻击者或内部异常节点不经过 APISIX，直接通过 IP 访问 `system-admin-service` 的高危接口
- **THEN** 服务端拦截器 MUST 发现该请求缺失正确的 `X-Internal-Sign`，并拒绝访问，返回 401 Unauthorized
