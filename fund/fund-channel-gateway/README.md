# fund-channel-gateway

CrediFlow 资金方接入网关（Go）。 本服务是平台与持牌资金方 / 银联代扣通道之间 **唯一的对外出口**，承担：

- 统一加签 / 验签 / AES·RSA 加解密钩子（按资金方协议可插拔）
- HTTPS + JSON 协议封装、超时、退避重试、熔断（sentinel-golang）
- 幂等防重（Redis SETNX；`providerId + businessOrderNo + operation`）
- 异步回调入口（一资金方一 URL）→ 验签 → 幂等 → RocketMQ 桥接事件
- 配置全部来自 Nacos（开发环境回退本地 YAML），多家资金方动态启停 / 切换
- Java↔Go 内网签名（`X-Internal-Sign`，与 `internal-api-security` 规格一致）

> 角色边界：用户 / 我方平台（Java + Go）/ 持牌资金方 / 支付代扣通道。资金 **只在资金方对公账户 ↔ 用户绑定储蓄卡** 之间流动；平台账户不过路。

## 快速开始

```bash
cd go/fund-channel-gateway
go mod tidy
FUND_GATEWAY_CONFIG_PATH=configs/fund-provider.dev.yaml go run .
# 网关默认监听 :8090
curl http://localhost:8090/health
```

## 端到端 mock 联调

`configs/fund-provider.dev.yaml` 默认启用 `mockProviderA`，每笔受理在配置的 `mockAsyncDelayMs` 后会自动触发模拟回调，把规范化后的 `FUND_DISBURSED_EVENT` / `REPAYMENT_SETTLED_EVENT` 投递给 RocketMQ；当 `rocketmq.nameServer` 为空时会落到 logger 打印路径，方便无 MQ 的纯本地开发。

签名有两种方式，**推荐 Java 调用方**与 `crediflow-common` 一致（`X-Timestamp` 毫秒 + Base64 HMAC，密钥默认 `default-secret-key-123`）：

```bash
TS=$(date +%s%3N 2>/dev/null || echo $(python -c "import time;print(int(time.time()*1000))"))
SECRET="default-secret-key-123"
PATH=/internal/v1/disburse
DATA="${PATH}${TS}"
SIGN=$(printf "%s" "$DATA" | openssl dgst -sha256 -hmac "$SECRET" -binary | openssl base64 -A)
curl -X POST http://localhost:8090/internal/v1/disburse \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: dev-trace-1" \
  -H "X-Timestamp: ${TS}" \
  -H "X-Internal-Sign: ${SIGN}" \
  -d '{
    "businessOrderNo": "L20260513-000001",
    "userId": "U1001",
    "bindCardId": "BC-token-demo",
    "amount": "12000.00",
    "currency": "CNY",
    "installments": 12,
    "triggerSource": "disburse-chain"
  }'
```

原生网关模式（秒级时间戳 + nonce + hex 签名，密钥同上）：

```bash
TS=$(date +%s)
NONCE=$(uuidgen)
CANON="POST\n/internal/v1/disburse\n${TS}\n${NONCE}"
SIGN=$(printf "$CANON" | openssl dgst -sha256 -hmac "default-secret-key-123" | awk '{print $2}')
curl -X POST http://localhost:8090/internal/v1/disburse \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: dev-trace-1" \
  -H "X-Internal-Timestamp: ${TS}" \
  -H "X-Internal-Nonce: ${NONCE}" \
  -H "X-Internal-Sign: ${SIGN}" \
  -d '{
    "businessOrderNo": "L20260513-000001",
    "userId": "U1001",
    "bindCardId": "BC-token-demo",
    "amount": "12000.00",
    "currency": "CNY",
    "installments": 12,
    "triggerSource": "disburse-chain"
  }'
```

返回 `{"state":"ACCEPTED", ...}`，约 2 秒后日志可见模拟回调与 MQ 桥接事件。

## 关键文档

- `docs/api-contract.md`：Java↔Go 内部 API 与回调契约。
- `docs/nacos-config.md`：Nacos DataId / Group / 字段约定与启动强校验。
- 业务规格：`openspec/changes/fund-provider-go-gateway/specs/**`。

## 阶段 0 不包含

- 真实资金方签名 / 加密算法（仅占位 hooks）
- `repayment-service` / `batch-service` / `post-loan-service` 侧 Java 改造与删除旧直连代码（后续批次）
- 持久化审计表（仅 SETNX 幂等 + 日志摘要）

> `fund-flow-service` 放款 Feign 与灰度开关已在 **第 2 批** 落地，默认 `crediflow.fund.use-gateway=false` 不改变既有行为。
