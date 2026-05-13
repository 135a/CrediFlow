# internal-api-security（Delta）

## ADDED Requirements

### Requirement: 人脸厂商回调入口的双层验签

系统 MUST 对人脸异步回调入口 `POST /api/internal/face-verify/callback` 同时实施两层验签：

1. **厂商签名**：由 `FaceVerifyProvider.verifySignature` 完成；签名规则、密钥、时间戳头由 Nacos 注入，MUST NOT 硬编码或与平台 `X-Internal-Sign` 共用密钥。
2. **平台内网约束（如启用 APISIX 反代 / 内网白名单）**：若回调由内部桥接代理，被代理后的请求 MUST 满足 `internal-api-security` 既有的 IP / 内网签名约束之一；MUST NOT 让外网直接打到内网回调接口。

两层验签 MUST 在调用任何业务逻辑（落库、状态更新、领域事件投递）之前完成；任一层失败 MUST 返回 401 / 厂商约定的失败语义并 MUST 写一条安全审计事件。

#### Scenario: 仅厂商签名通过、内网约束不满足

- **WHEN** 攻击者从外网伪造一条厂商签名合法但绕过 APISIX 的回调
- **THEN** 系统 MUST 在内网入口拦截层拒绝（IP 不在白名单或缺少边缘标记头）；MUST NOT 进入 `FaceVerifyProvider.verifySignature` 之后的业务逻辑

#### Scenario: 厂商签名失败

- **WHEN** 回调 payload 被中间网络节点篡改导致厂商签名校验失败
- **THEN** 系统 MUST 返回 401；MUST NOT 写 `cf_face_verify_log` 业务终态；MUST 写安全审计

### Requirement: 风控与用户服务的内网反查接口

系统 MUST 把以下内部反查接口纳入 `internal-api-security` 既有签名校验：

- `POST /api/internal/risk/blacklist/check`（`credit-risk-service` 暴露给 `user-service`）
- `POST /api/internal/risk/kyc/lookup-by-fingerprint`（如启用，由 `user-service` 暴露给 `credit-risk-service`）
- `GET /api/internal/user/eligibility`（`user-service` 暴露给 `credit-application` / `loan-application`，返回 `{kycPassed, hasPrimaryBankCard}`）

这些接口 MUST 仅在内网可达（APISIX 不对外暴露）；调用方 MUST 携带合法 `X-Internal-Sign + X-Timestamp`；接口 body / 查询参数中 MUST 不出现明文姓名 / 身份证号 / 卡号 / 预留手机号；MUST 仅使用 `userId`、`idCardFingerprint`、`cardNoFingerprint`、`bindCardId` 等脱敏标识。

#### Scenario: 跨服务反查仅传指纹

- **WHEN** `user-service` 调用 `/api/internal/risk/blacklist/check`
- **THEN** 请求 body MUST 仅含 `idCardFingerprint` 字段；MUST NOT 包含明文姓名或身份证号

#### Scenario: 非法签名跨服务反查

- **WHEN** 调用方未带 `X-Internal-Sign` 或时间戳漂移超限调用 `/api/internal/user/eligibility`
- **THEN** 服务 MUST 返回 401；MUST NOT 进入业务逻辑

### Requirement: 非生产后门接口的强制访问控制

非生产环境下用于跳过人脸 / 直接置 KYC 通过的后门接口（如 `/api/internal/test/kyc/force-pass`）MUST 同时满足：

- 标注 `@Inner`（或等价仅内网可达）；
- 仅在 `@Profile("!prod")` 等价集合下注册；
- 走 `internal-api-security` 内网签名校验；
- 调用 MUST 写一条 `channel=BACKDOOR` 的审计流水。

生产环境集合下 MUST 完全不暴露这些接口（外部请求 MUST 返回 404）。

#### Scenario: 生产 profile 不暴露后门

- **WHEN** Spring 启动激活生产 profile 后端口监听
- **THEN** 任何外部请求 `/api/internal/test/kyc/*` MUST 返回 404；MUST NOT 出现在 Spring 注册的端点列表
