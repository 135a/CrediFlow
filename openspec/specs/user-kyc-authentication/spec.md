# user-kyc-authentication Specification

## Purpose

定义用户 KYC（Know Your Customer）认证生命周期。KYC v2 将原「基础信息 + 二要素 + 简单支付账号」重写为「二要素 + 准入闸门 / 人脸实人 / KYC 通过门槛」；银行卡四要素绑卡为独立能力（见 `bankcard-four-elements-binding`）。

## Requirements

### Requirement: 三步 KYC 认证生命周期（KYC v2）

系统 MUST 提供经过重新定义的 KYC 三步生命周期：

- **step1**：姓名 + 身份证号二要素第三方一致性核验，**且 MUST 先通过 `kyc-eligibility-gate` 的全部准入闸门**（18–55 / 身份证唯一 / 黑名单 / 限流）；
- **step2**：基于 `kyc-face-liveness` 的人脸实人核验（活体 + 公安底照比对），由单一第三方厂商端到端完成，终态由异步回调驱动；
- **step3**：标记 KYC 已通过的稳态步骤（无独立用户操作）。

系统 MUST 在新表 `cf_user_kyc_v2` 中并行维护 `eligibility_status`、`realname_status`、`face_status` 三条独立状态；当且仅当 `realname_status=VERIFIED ∧ face_status=VERIFIED` 时 MUST 将 `kyc_passed=1` 并写 `kyc_passed_at`。系统 MUST 支持用户在中断后恢复到最近一步未完成的状态；任意一步失败 MUST NOT 错误地将后续步骤标记为通过。

#### Scenario: 用户首次发起 KYC v2

- **WHEN** 已登录用户首次进入 KYC v2 入口
- **THEN** 系统 MUST 引导用户进入 step1（二要素 + 准入闸门）；MUST 不读取或要求填写月收入、居住地等旧画像字段作为 KYC 通过条件

#### Scenario: 二要素通过但人脸未通过

- **WHEN** `realname_status=VERIFIED ∧ face_status≠VERIFIED`
- **THEN** 系统 MUST NOT 设置 `kyc_passed=1`；任何依赖 KYC 通过的下游能力（绑卡、授信、借款）MUST 拒绝放行

#### Scenario: 断点续传

- **WHEN** 已完成 step1 的用户重新进入 KYC v2 入口
- **THEN** 系统 MUST 直接进入 step2（人脸核验入口）；MUST NOT 强制要求重复提交二要素

#### Scenario: 准入闸门未过不进二要素

- **WHEN** 用户在 step1 触发准入闸门拒绝（年龄超限 / 重复绑卡 / 黑名单）
- **THEN** 系统 MUST NOT 调用第三方实名 Provider；MUST NOT 进入 step2 人脸入口

### Requirement: 未通过 KYC 不得进入绑卡与授信

系统 MUST 在所有依赖 KYC 通过的下游接口入口校验 `kyc_passed=1`：

- 银行卡四要素绑卡（`bankcard-four-elements-binding`）
- 授信额度开通（`credit-application-lifecycle`）
- 借款放款受理（`microservice-loan-application`）

未通过 KYC 的用户 MUST 被这些接口拒绝并 MUST NOT 在 API 返回中暴露 `face_status` / `realname_status` 等内部字段的失败原因（仅返回「请先完成 KYC 实名实人核验」统一文案）。

#### Scenario: 未 KYC 强进绑卡

- **WHEN** `kyc_passed=false` 的用户请求 `/api/app/user/bankcard/bind`
- **THEN** 系统 MUST 返回 403 或业务约定拒绝；MUST NOT 进入四要素鉴权链路

#### Scenario: 未 KYC 强进借款

- **WHEN** `kyc_passed=false` 的用户请求借款受理接口
- **THEN** `microservice-loan-application` MUST 在受理前拒绝并 MUST NOT 调用 `fund-channel-gateway`

### Requirement: KYC 通过领域事件

系统 MUST 在 `kyc_passed` 由 0 推进到 1 的同一事务（或经 outbox 模式）投递 `KYC_PASSED_EVENT` 到 RocketMQ，载荷至少含 `userId`、`realnameProviderTxnNo`、`faceProviderTxnNo`、`passedAt`、`idCardMask`。`credit-application-lifecycle`、`microservice-post-loan`、`microservice-loan-application` 等下游 MUST 仅订阅该事件感知 KYC 通过；MUST NOT 反向轮询 `cf_user_kyc_v2`。

#### Scenario: KYC 通过后下游订阅

- **WHEN** 用户的 `face_status` 终态从 `PROCESSING` 变为 `VERIFIED` 且 `realname_status` 已是 `VERIFIED`
- **THEN** 系统 MUST 投递 `KYC_PASSED_EVENT`；下游消费者 MUST 以此事件作为触发点

### Requirement: 新表 cf_user_kyc_v2 与数据迁移

系统 MUST 启用新表 `cf_user_kyc_v2` 承载 KYC v2 全部状态字段，字段至少包含：`user_id`（唯一）、`real_name`、`id_card_no`（AES 密文）、`id_card_mask`、`id_card_fingerprint`（HMAC，唯一索引）、`age_at_submit`、`eligibility_status`、`realname_status`、`realname_provider_txn_no`、`realname_verified_at`、`face_status`、`face_provider_id`、`face_provider_biz_no`、`face_provider_txn_no`、`face_verified_at`、`face_failure_code`、`kyc_passed`、`kyc_passed_at`、时间戳。系统 MUST 通过 Flyway 迁移把旧 `cf_user_kyc` 中 `realname_status=VERIFIED` 的用户数据搬运到 `cf_user_kyc_v2`（实名沿用、人脸状态置 `NOT_SUBMITTED`），并 MUST 保留旧表 90 天只读用于回滚与对账；旧 step3 的 `payment_method/payment_account` MUST NOT 搬运到 `cf_user_bank_card`（缺四要素无法直接复用）。

#### Scenario: 旧表只读保护

- **WHEN** Flyway 迁移完成后任何 KYC 写入请求到达
- **THEN** 系统 MUST 仅写 `cf_user_kyc_v2`；MUST NOT 写 `cf_user_kyc`

#### Scenario: 旧实名用户人脸补做

- **WHEN** 旧 `cf_user_kyc.realname_status=VERIFIED` 的用户重新进入 KYC v2 入口
- **THEN** 系统 MUST 跳过 step1 二要素重复核验（实名沿用）；MUST 要求其完成 step2 人脸核验后才能 `kyc_passed=1`

## Removed (historical)

### ~~第三步收款账号绑定（旧 step3）~~

**Reason**：旧 step3 仅保存 `payment_method/payment_account` 字符串字段，不调用任何第三方四要素鉴权，不与放款链路联动，无法满足资金合规要求。该步骤被全新能力 `bankcard-four-elements-binding` 替代，由独立 Controller `/api/app/user/bankcard/*` 承载。

**Migration**：旧 step3 字段保留在 `cf_user_kyc`（只读 90 天用于回滚），不向 `cf_user_bank_card` 搬运；存量用户进入 KYC v2 后需重新完成四要素绑卡。
