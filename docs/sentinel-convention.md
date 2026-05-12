# Sentinel 限流与网关分层说明

## 1. 分层限流策略
- **APISIX/Spring Cloud Gateway 层**：负责**业务无关的粗粒度限流**。如：单 IP 的总体 QPS 限制、恶意刷接口的 IP 黑名单防护。
- **Sentinel 层 (微服务内部)**：负责**业务相关的细粒度限流与熔断**。如：某个具体方法的 QPS 限制、服务间调用的超时熔断、热点参数限流（如针对特定 UserId 的高频借款申请）。

## 2. 资源名规则
在业务代码中定义 Sentinel 资源时，使用如下命名规范：
- HTTP 接口：默认为请求路径，例如 `POST:/api/v1/loan/apply`
- RPC 接口 (Feign)：`${服务名}:${类名}#${方法名}`
- 自定义业务逻辑：使用 `@SentinelResource` 注解，资源名规则为 `${模块名}_${业务动作}`，例如 `credit_risk_evaluation`。

## 3. 最小演示规则示例
使用 Nacos 动态下发 Sentinel 规则：
```json
[
  {
    "resource": "loan_apply_submit",
    "limitApp": "default",
    "grade": 1, 
    "count": 10,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```
以上规则限制 `loan_apply_submit` 资源 QPS 不得超过 10。
