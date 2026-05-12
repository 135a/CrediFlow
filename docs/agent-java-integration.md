# Agent 与 Java 服务间交互示例与超时/熔断配置 (11.6)

## 1. Java 调用 Agent (风控评估)
- **协议**: HTTPS / HTTP
- **工具**: OpenFeign
- **熔断与降级配置**:
  在 `credit-risk-service` 的 `AgentClient` 中，如果 `data-agent` 响应超时或不可用，将触发 `AgentClientFallback`。
  
  ```properties
  # application.yml 示例
  feign.client.config.agent-service.connectTimeout: 2000
  feign.client.config.agent-service.readTimeout: 5000
  feign.circuitbreaker.enabled: true
  ```
  *降级逻辑*: 若 AI Agent 超时，系统将分配基础安全额度 (如 5000.00)，或者挂起状态转交人工审核。

## 2. Agent 调用 Java (NL2API)
- **协议**: HTTP GET
- **限制**: 采用**白名单机制**，在 `nl2api.py` 中硬编码允许访问的接口路由。
- **参数校验**: (11.5) 通过 JSON Schema 或固定字典过滤 LLM 提取的参数。
- **超时**: `requests.get(url, timeout=5)`，严防长时间挂起。
- **鉴权**: 内部请求携带 `X-Inner-Call: true`（若配置了 mTLS/JWT，需要额外加载对应凭证）。
