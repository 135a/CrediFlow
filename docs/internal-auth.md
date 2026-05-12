# 微服务内网互信方案 (JWT)

为了防止内部微服务接口被非法绕过网关直接调用，采用轻量级内部 JWT 互信机制。

## 机制
1. 每个发起 Feign 调用的服务，会在请求头中通过 `InternalAuthInterceptor` 注入一个短期有效的内部 JWT Token。
2. 接收方通过 `InternalAuthFilter` 拦截并验证该 Token 的有效性。
3. 双方共享同一套密钥。
