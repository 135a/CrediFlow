# Java ↔ Go 内部 API 契约 · fund-channel-gateway

本文件锁定 Java 业务微服务调用 Go 资金网关的 **内部** API，以及资金方回调网关的 **对外** API。
所有调用走 HTTPS（生产）或 HTTP（同 K8s/Compose 内网）。

> 阶段 0 仅在 Go 网关侧实现；**第 2 批** 起 `fund-flow-service` 已通过 Feign 接入放款受理接口。

## 路由总览

| 方向                  | 方法 / 路径                                        | 鉴权                       | 备注                                |
| --------------------- | --------------------------------------------------- | -------------------------- | ----------------------------------- |
| Java → Go             | `POST /internal/v1/disburse`                        | `X-Internal-Sign`          | 放款受理                            |
| Java → Go             | `POST /internal/v1/repay`                           | `X-Internal-Sign`          | 主动还款受理                        |
| Java / Scheduler → Go | `POST /internal/v1/withhold`                        | `X-Internal-Sign`          | 到期 / 定时代扣受理                  |
| Provider → Go         | `POST /fund/callback/:providerId`                   | 资金方报文签名（非内网签名）| 异步终态回调                        |
| Public                | `GET /health`、`GET /ready`                         | 无                         | 容器探针                            |

## 内网签名约定（Java → Go）

每次请求附加三个头部：

| Header                | 示例                              | 说明                                   |
| --------------------- | --------------------------------- | -------------------------------------- |
| `X-Request-Id`        | （来自 FeignTraceInterceptor）     | 与 Java 链路追踪一致；网关亦接受 `X-Trace-Id` |
| `X-Timestamp`         | `1715600000123`                   | **毫秒** Unix 时间戳（与 `crediflow-common` `InternalAuthRequestInterceptor` 一致） |
| `X-Internal-Sign`     | Base64(HMAC-SHA256)               | 签名字符串 = `requestPath + X-Timestamp`（path 为 Gin `URL.Path`，如 `/internal/v1/disburse`） |

密钥：与 Java `crediflow.internal.secret` 及 Nacos `internalSign.sharedSecret` **必须一致**（仓库 dev 默认均为 `default-secret-key-123`）。

**可选原生模式**（curl / 非 Java 客户端）：`X-Internal-Timestamp`（**秒**）+ `X-Internal-Nonce` + `X-Internal-Sign`（hex，HMAC 规范 `METHOD` + 换行 + `FullPath` + 换行 + `ts` + 换行 + `nonce`）。

Java 侧示例（伪代码）：

```java
String ts = String.valueOf(System.currentTimeMillis());
String path = "/internal/v1/disburse";
String data = path + ts;
String sign = Base64.getEncoder().encodeToString(HmacSHA256(data, sharedSecret));
headers.set("X-Timestamp", ts);
headers.set("X-Internal-Sign", sign);
```

> 旧版文档中仅描述 hex+nonce 模式；以上 Java 兼容模式为阶段 2 起与现有微服务对齐所必需。

## 受理请求（Java → Go）

### `POST /internal/v1/disburse` · 放款受理

```json
{
  "providerId": "providerA",
  "businessOrderNo": "L20260513-000001",
  "userId": "U1001",
  "bindCardId": "BC-token-xxxxxxxx",
  "amount": "12000.00",
  "currency": "CNY",
  "installments": 12,
  "triggerSource": "disburse-chain",
  "extra": { "applicationId": "A20260513-001" }
}
```

字段约束：

- `providerId`：可选；缺省走 Nacos `defaultProviderId`（决策 #2 混合路由）。
- `bindCardId`：**必须** 是绑卡 token / 引用 ID；Java 严禁传明文卡号四要素（决策 #3）。
- `amount`：十进制字符串，避免 float 精度丢失。
- `installments`：可选；放款 / 还款 / 代扣中按业务需要。

### `POST /internal/v1/repay` · 主动还款受理

请求体同上；通常 `triggerSource: "active"`，`installments` 表示要核销的期次。

### `POST /internal/v1/withhold` · 定时代扣受理

请求体同上；通常 `triggerSource: "scheduler"`（由 `go/batch-service` 调用，第 3 批实施）。

### 响应：受理态（同步只表态，不返回终态）

```json
{
  "state": "ACCEPTED",
  "gatewayRequestId": "GW-1f8b...",
  "providerId": "providerA",
  "businessOrderNo": "L20260513-000001",
  "errorCode": "",
  "errorMessage": ""
}
```

`state` 取值（与 `provider/client.go::ReceiptState` 一一对应）：

| state            | HTTP | 含义                                                                     |
| ---------------- | ---- | ------------------------------------------------------------------------ |
| `ACCEPTED`       | 202  | 网关已接收并提交资金方；终态等回调                                       |
| `REJECTED`       | 422  | 资金方明确拒绝（业务参数 / 业务规则）                                    |
| `RETRYABLE`      | 502  | 网关重试后仍失败（超时、5xx）；Java 视为重试类错误，可后续补偿           |
| `CIRCUIT_OPEN`   | 503  | 熔断打开，快速失败                                                       |
| `CONFIG_ERROR`   | 503  | Nacos 配置缺失 / 加签失败；运维介入                                       |

幂等：相同 `(providerId, businessOrderNo, operation)` 的重复请求返回与首次一致的受理态，并附带 `errorCode: DUPLICATE_RECEIPT`，**绝不** 向资金方重复下单。

## 异步回调（Provider → Go）

资金方按其文档要求 POST 到 `/fund/callback/:providerId`，由资金方签名（**非** 内网签名）保护。回调请求体规范化为：

```json
{
  "businessOrderNo": "L20260513-000001",
  "providerTxnNo": "PA-20260513-9988",
  "operation": "DISBURSE",
  "terminal": "SUCCESS",
  "amount": "12000.00",
  "currency": "CNY",
  "applicationId": "A20260513-001",
  "userId": "U1001",
  "loanNo": "LN-1001",
  "installment": 0,
  "sign": "...",
  "timestamp": 1715600000,
  "extra": {}
}
```

`operation` 取值：`DISBURSE` / `REPAY` / `WITHHOLD`。
`terminal` 取值：`SUCCESS` / `FAILED`（其它一律按 `FAILED` 处理）。

网关处理流程：

1. 读取原始 body → 计算 SHA-256 摘要写入审计（不存原文）。
2. 资金方签名校验（阶段 0 由 `provider.Cipher` / `provider.Signer` 桩占位；第 2 批接入真实算法）。
3. 以 `(providerId, providerTxnNo)` 作回调级幂等键，Redis SETNX 短路重复回调。
4. 将终态写入对应的 RocketMQ 桥接事件并发布（决策 #1 即方案 B）。
5. 同步回响应：`{"code":"SUCCESS","message":"ack"}`。

## RocketMQ 桥接事件（Go → Java）

| Topic                       | 触发 op                | Java 消费方                                |
| --------------------------- | ---------------------- | ------------------------------------------ |
| `FUND_DISBURSED_EVENT`      | `DISBURSE`             | `fund-flow-service`、`repayment-service`（生成还款计划）、`post-loan-service` |
| `REPAYMENT_SETTLED_EVENT`   | `REPAY` / `WITHHOLD`   | `repayment-service`、`post-loan-service`、画像/风控 outbound 通道 |

事件 schema 见 `mq/events.go`，与 OpenSpec 中 `microservice-fund-flow` / `microservice-repayment` / `microservice-post-loan` delta spec 对齐。
