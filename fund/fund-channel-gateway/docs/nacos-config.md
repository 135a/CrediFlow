# Nacos 配置约定 · fund-channel-gateway

本文件锁定阶段 0 的 Nacos 配置中心使用约定。所有资金方对外参数、内网签名密钥、回调白名单 **必须** 通过 Nacos 注入；仓库内 `configs/*.yaml` 仅用于本地开发占位。

## DataId / Group / Namespace

| 维度        | 值                                                | 备注 |
| ----------- | ------------------------------------------------- | ---- |
| DataId      | `fund-provider.yaml`                              | 默认；多资金方时可拆为 `fund-provider-{providerId}.yaml`，由网关在启动时按列表合并加载（待后续批次实现） |
| Group       | `FUND_PROVIDER_GROUP`                             | 与其他业务组隔离，便于 ACL |
| Namespace   | 按环境区分：`dev`、`test`、`prod`                 | 通过 `FUND_GATEWAY_NACOS_NAMESPACE` 环境变量传入 |

启动顺序：

1. 网关读取 `FUND_GATEWAY_NACOS_SERVER`（如 `http://nacos:8848`）。
2. 若未设置，则回退到本地文件路径 `FUND_GATEWAY_CONFIG_PATH`（默认 `configs/fund-provider.dev.yaml`）。
3. 调用 Nacos Open API `GET /nacos/v1/cs/configs?dataId=...&group=...&tenant=...` 取 YAML 文本。
4. YAML 解析后执行 `config.Validate`：缺失必填项一律 **拒绝启动**。

> 阶段 0 直接走 Nacos HTTP API（无 SDK 依赖）；阶段 2 切换到 `nacos-sdk-go` 以支持长轮询热更新。

## 配置字段

```yaml
env: prod                       # dev | test | prod。生产环境禁止 useMock。

http:
  addr: ":8090"
  readTimeoutMs: 10000
  writeTimeoutMs: 10000

internalSign:                   # Java ↔ Go 内网签名（X-Internal-Sign）
  headerName: "X-Internal-Sign"
  timestampSkewSeconds: 300
  sharedSecret: "<由运维注入>"
  disabled: false               # 生产环境必须 false，启动校验会强制断言

defaultProviderId: providerA    # 决策 #2 混合路由：Java 不传 providerId 时使用

providers:
  providerA:
    enabled: true
    displayName: "Provider A"
    baseUrl: "https://api.provider-a.example.com"
    appKey: "<注入>"
    appSecret: "<注入>"
    signAlgorithm: "HMAC_SHA256"   # 资金方实际算法（HMAC_SHA256 / RSA_SHA256 / ...）
    encryptFields: ["bindCardId"]  # 需要 AES/RSA 加密的字段名
    aesKey: "<注入>"
    rsaPublicKey: "<注入>"
    rsaPrivateKey: "<注入>"
    httpTimeoutMs: 5000
    maxRetries: 2
    retryBackoffMs: 500
    circuitBreaker:
      errorRatioThreshold: 0.5
      minRequestAmount: 5
      statIntervalMs: 10000
      retryTimeoutMs: 30000
    callbackPaths:
      - "/fund/callback/providerA"
    callbackIpAllow:
      - "203.0.113.10"
    useMock: false                  # 生产强制 false；启动校验会断言

redis:
  addr: "redis:6379"
  password: "<注入>"
  db: 0
  idempotencyTtlSeconds: 86400

rocketmq:
  nameServer: "rmqnamesrv:9876"
  groupId: "fund-channel-gateway"
  disburseTopic: "FUND_DISBURSED_EVENT"
  repayTopic: "REPAYMENT_SETTLED_EVENT"

audit:
  logPayloadDigest: true
```

## 强校验（启动阶段）

| 检查项                                                        | 失败行为     |
| -------------------------------------------------------------- | ------------ |
| `defaultProviderId` 必须指向已存在的 provider                  | 拒绝启动     |
| `internalSign.sharedSecret` 非空（除非 `disabled=true` 且非 prod） | 拒绝启动     |
| 生产环境 `internalSign.disabled` 必须为 false                  | 拒绝启动     |
| 生产环境任何 provider `useMock=true`                            | 拒绝启动     |
| `useMock=false` 的 provider 必须有 `baseUrl` / `appKey` / `appSecret` / `signAlgorithm` | 拒绝启动 |

> 这些断言由 `config/config.go::Validate` 实现，覆盖 OpenSpec 规格中 `fund-provider-go-gateway/spec.md` 的 *Requirement: Nacos 外置配置与禁止硬编码*。

## 密钥与脱敏

- 资金方 `appSecret` / `aesKey` / `rsa*Key` 与 `internalSign.sharedSecret` **不得** 写入 Git；在 Nacos 中开启 ACL 仅允许网关账号读取，并启用配置审计日志。
- 网关日志在 `logger/logger.go::sanitize` 中对身份证号、银行卡号、`*Secret` / `*Key` 字段做最后一道掩码。任何上游打印未脱敏的字段都会被自动遮盖。
