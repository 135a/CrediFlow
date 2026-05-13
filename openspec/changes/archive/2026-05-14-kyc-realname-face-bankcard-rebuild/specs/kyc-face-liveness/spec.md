# kyc-face-liveness

## Purpose

定义 KYC 人脸实人核验能力：通过抽象 `FaceVerifyProvider`，把前端活体采集 + 公安身份证底照比对统一委托给单一第三方厂商端到端完成；终态通过异步回调驱动；先内置 Mock 与 HTTP 占位实现，Nacos 动态切换厂商。该能力是 KYC 通过的必要条件之一（与二要素并列），与「银行卡四要素绑卡」彻底解耦。

## ADDED Requirements

### Requirement: FaceVerifyProvider 抽象与端到端单厂商核身

系统 MUST 通过统一接口 `FaceVerifyProvider`（或等价命名）抽象人脸实人核验，把「前端 SDK 活体采集 + 厂商内部公安人像底照比对」作为一次端到端 Provider 调用；MUST NOT 在我方代码中拆分为「活体 token 拿取」与「比对」两段并由前端串接业务密钥。系统 MUST 通过 Nacos（或等价配置中心）的 `kyc.face.provider.active` 等参数动态切换实现（如 `mock` / `http` / `<vendorId>`），新增厂商 MUST 仅通过新增实现类与配置即可上线，MUST NOT 要求修改 KYC 主流程控制器代码。

#### Scenario: 切换厂商仅改配置

- **WHEN** 运维在 Nacos 切换 `kyc.face.provider.active` 与对应厂商参数
- **THEN** 系统 MUST 在不重写 KYC 主流程的前提下，把新提交的人脸核验请求路由到新实现并按其约定的请求体与签名外呼

#### Scenario: 单厂商一次性核身

- **WHEN** 用户在前端完成活体动作并由 SDK 拿到厂商业务 token
- **THEN** 系统 MUST 仅向所选 Provider 发起一次 `submit` 调用，由厂商内部完成活体校验 + 公安底照比对并最终通过异步回调回传结论；MUST NOT 在我方应用层拼接活体片段与公安比对结果

### Requirement: 异步回调入口与验签幂等

系统 MUST 对外暴露 `POST /api/internal/face-verify/callback`（或等价路径）作为厂商终态回调的唯一入口。该入口 MUST 调用 `FaceVerifyProvider.verifySignature` 完成厂商签名验证；MUST 以 `(providerId, providerTxnNo)` 维度做幂等（Redis SETNX 或等价存储），重复回调 MUST 返回成功 ACK 但 MUST NOT 重复更新业务状态或重复投递领域事件。系统 MUST 持久化回调流水（含 `payload_digest`、`callback_received_at`、`failure_code` 内部原因码），明文敏感字段 MUST NOT 入库或入日志。

#### Scenario: 回调签名无效

- **WHEN** 厂商回调签名验证失败（含被篡改 payload 或时间戳漂移超限）
- **THEN** 系统 MUST 返回 401 或厂商约定的失败语义；MUST NOT 更新 `face_status`；MUST 写一条带 `signatureValid=false` 的安全审计

#### Scenario: 重复回调短路

- **WHEN** 同一 `providerTxnNo` 在 24 小时内被厂商重复回调
- **THEN** 系统 MUST 仅在首次处理时写终态与投递领域事件；后续重复回调 MUST 立即返回成功 ACK 且 MUST NOT 再次写库

### Requirement: 中间态与前端轮询语义

系统 MUST 在 Provider 同步受理后立即把用户的 `face_status` 置为 `PROCESSING` 并在 Redis 写入 `face:state:<userId>:<bizNo>` 中间态键，TTL 不少于 30 分钟。系统 MUST 提供查询接口（如 `GET /api/app/user/kyc/v2/status`）供前端轮询；中间态查询 MUST NOT 触发对厂商的二次外呼。终态到达后该 Redis 键 MUST 被更新或清除以反映最终结论。

#### Scenario: 终态未到的前端轮询

- **WHEN** 前端在 Provider 受理但回调未达的时间窗内轮询 KYC 状态
- **THEN** 系统 MUST 返回 `face_status=PROCESSING` 的清晰语义；MUST NOT 因轮询触发厂商重新外呼

#### Scenario: 终态到达后的轮询

- **WHEN** 异步回调已处理完毕且写库成功
- **THEN** 下一次轮询 MUST 返回最终的 `face_status` 与（若失败）用户可读摘要，且 MUST 与 `kyc_passed` 字段保持一致

### Requirement: 非生产 Mock 与生产强断言

系统在非生产环境 MAY 通过 Nacos `kyc.face.verify.mock=true` 直接判定人脸 SUCCESS 并跳过厂商外呼与前端 SDK，但 MUST 走与生产一致的状态机、回调流水（`channel=MOCK`）与领域事件投递。系统在生产环境（由 Spring profile 或等价生产 profile 集合判定）若检测到 `kyc.face.verify.mock=true` MUST 在 Spring 启动阶段抛出致命错误并 MUST 中止应用启动；该检查 MUST 在接受任何流量之前完成。

#### Scenario: 测试环境 Mock 跑通完整链路

- **WHEN** 非生产环境配置 `kyc.face.verify.mock=true` 且测试用户提交 step2
- **THEN** 系统 MUST 不唤起摄像头、不调任何厂商 API；MUST 在 `cf_face_verify_log` 写一条 `channel=MOCK status=SUCCESS` 流水并把 `face_status=VERIFIED`；MUST 触发 `KYC_PASSED_EVENT`（若二要素也已 VERIFIED）

#### Scenario: 生产误开 Mock 拒绝启动

- **WHEN** 当前激活 profile 属于生产集合且 Nacos 中 `kyc.face.verify.mock=true`
- **THEN** 系统 MUST 抛出致命异常并 MUST 拒绝完成 Spring 上下文启动

### Requirement: 白名单跳过与后台干预（仅非生产）

系统在非生产环境 MAY 支持手机号 / 身份证指纹白名单（`kyc.face.verify.whitelist`），命中白名单的用户在 step2 MUST 与 Mock 路径等价（`channel=WHITELIST`）。系统 MAY 暴露后台干预接口（如 `POST /api/internal/test/kyc/force-pass`）允许把指定 `userId` 直接置为 `kyc_passed=1`，该接口 MUST 同时满足：标注内部接口（`@Inner` 或等价）、限 `@Profile("!prod")`、走 `internal-api-security` 内网签名拦截。生产环境 MUST NOT 暴露该后门接口。

#### Scenario: 白名单测试账号

- **WHEN** 非生产环境某测试手机号配置在白名单
- **THEN** 系统 MUST 在该用户 step2 直接判 SUCCESS 且写 `channel=WHITELIST` 流水

#### Scenario: 生产环境不暴露后门

- **WHEN** 系统启动于生产 profile
- **THEN** 后台干预接口 MUST 完全不注册到 Spring 上下文，外部请求该路径 MUST 返回 404 或等价未实现语义

### Requirement: 网络故障与业务失败的明确分支

系统 MUST 区分人脸 Provider 调用的「可重试错误」与「终态失败」。遇到读超时、连接失败、HTTP 5xx 时 MUST NOT 把 `face_status` 写为 FAILED 终态；MUST 让前端可重新发起。遇到厂商明确返回「活体失败」「比对不一致」「证件失效」等终态业务结论时 MUST 写 `face_status=FAILED` 并记录内部原因码；用户可见摘要 MUST 通过受控字段返回，MUST NOT 透出厂商内部错误码。

#### Scenario: 厂商外呼超时

- **WHEN** Provider `submit` 读超时
- **THEN** 系统 MUST NOT 写终态；MUST 返回可重试语义错误码；MUST 在流水中标记 `retryable=true`

#### Scenario: 厂商明确比对失败

- **WHEN** 厂商回调返回明确的「人像不一致」终态
- **THEN** 系统 MUST 写 `face_status=FAILED`、`failure_code` 内部原因码；MUST 给前端可读摘要而非直接透出厂商原始错误码

### Requirement: 流水持久化与领域事件

系统 MUST 对每次人脸核验持久化一条 `cf_face_verify_log` 流水，至少包含：`user_id`、`provider_id`、`provider_biz_no`（我方下发的业务单号）、`provider_txn_no`（厂商终态流水号）、`status`、`payload_digest`、`channel`（MOCK/WHITELIST/HTTP）、`duration_ms`、`callback_received_at`。当 `realname_status` 与 `face_status` 同时为 `VERIFIED` 时系统 MUST 投递 `KYC_PASSED_EVENT` 领域事件到 RocketMQ，供下游 credit / loan / post-loan 服务订阅。

#### Scenario: KYC 通过事件投递

- **WHEN** 用户的 `realname_status` 已是 VERIFIED，新到达的人脸回调把 `face_status` 由 PROCESSING 推为 VERIFIED
- **THEN** 系统 MUST 在同一事务（或经 outbox 模式）投递 `KYC_PASSED_EVENT`，载荷至少含 `userId`、`realnameProviderTxnNo`、`faceProviderTxnNo`、`passedAt`
