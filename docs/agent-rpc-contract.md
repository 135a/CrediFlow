# Agent 间 RPC 调用规范与示例 (Java <-> Python)

为保障多技术栈环境下的系统稳定性，所有跨 Agent 的同步 RPC 调用（如 Java 合约 Agent 调用 Python 风控 Agent）均须遵守以下契约规范。

## 1. 统一头部信息透传
所有的跨界请求必须在 HTTP Header 中附带追踪与身份信息，以便于在日志系统和网关中溯源：
- `X-Trace-Id`: UUID，标识单次全局业务链路。
- `X-Agent-Source`: 标识调用方 Agent，如 `agent-contract-process`。
- `Authorization`: 统一的 JWT 或内部服务间验签 Token（可选配置）。

## 2. Python 风控/定价接口规范示例

所有的返回体均使用统一结构 `{"code": 200, "message": "success", "data": {...}}`。

### 示例 1: 获取用户风险评级 (Anti-Fraud & Risk Agent)
- **Method**: `POST`
- **Path**: `/api/risk/evaluate`
- **Request Body**:
```json
{
  "userId": 10001,
  "applicationId": 88021,
  "idCard": "11010519900101XXXX",
  "requestedAmount": 5000.00
}
```
- **Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "riskLevel": "LOW",          // LOW, MEDIUM, HIGH
    "passed": true,              // 是否通过初审
    "rejectionReason": null      // 拒绝原因（如果未通过）
  }
}
```

### 示例 2: 获取动态利率与额度 (Credit Limit & Pricing Agent)
- **Method**: `POST`
- **Path**: `/api/pricing/calculate`
- **Request Body**:
```json
{
  "userId": 10001,
  "riskLevel": "LOW"
}
```
- **Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "maxCreditLimit": 10000.00,
    "annualInterestRate": 0.12,  // 动态年化 12%
    "penaltyRate": 0.0005        // 动态逾期费率 0.05%
  }
}
```

## 3. Java 调用方约束
Java 端使用 Spring Cloud OpenFeign 进行调用时，必须配置对应的 fallback 机制，对于超时和 5xx 错误，必须捕获并转换为 `AgentAsyncRetryException`（针对异步流程）或向前端抛出标准业务提示。绝对禁止因为单次网络抖动导致上游长事务回滚且无法恢复的情况。
