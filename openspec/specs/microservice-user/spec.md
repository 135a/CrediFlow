# microservice-user Specification

## Purpose

定义用户域微服务的注册登录、会话与令牌、敏感个人信息保护与脱敏、内部手机号反查等基础能力；并承载 KYC v2（二要素+闸门+人脸）、银行卡四要素绑卡、人脸异步回调与内部风控/授信反查接口的对外与对内契约。旧 `cf_user_kyc.step_status` 与 step3 支付字段不再作为 KYC 通过依据。

## Requirements

### Requirement: 用户注册与认证

用户服务 MUST 提供注册、登录与会话/令牌签发能力；MUST 支持短信验证码与人脸认证等扩展认证方式的接口占位与策略开关（未启用时 MUST 明确拒绝或返回未实现语义）。

#### Scenario: 登录成功签发令牌

- **WHEN** 用户提交正确的凭证且账户状态允许登录
- **THEN** 系统 MUST 签发 JWT（或由网关统一签发策略对接）并 MUST 记录登录审计事件

### Requirement: 敏感个人信息保护与脱敏

系统 MUST 对身份证号、手机号等敏感字段加密存储；对外查询接口 MUST 默认脱敏展示；任何导出或日志 MUST NOT 明文输出完整敏感字段。为了支持业务微服务的关联检索，系统 MUST 提供内部的手机号反查接口。

#### Scenario: 查询用户详情默认脱敏

- **WHEN** 授权用户查询另一用户的个人信息接口
- **THEN** 响应 MUST 对敏感字段按脱敏规则返回且 MUST 记录访问审计

#### Scenario: 内部服务根据手机号解析用户ID

- **WHEN** 内部信贷或借款微服务提供明文/密文手机号请求解析时
- **THEN** 系统 MUST 精确匹配对应的用户，并仅向内部调用方返回对应的 `userId`，而不能泄露其他敏感信息

### Requirement: 用户画像采集边界

用户服务 MUST 仅采集配置中显式启用的画像字段；画像变更 MUST 可追溯到操作者与时间；用户服务 MUST 通过独立的 **KYC v2** API（`/api/app/user/kyc/v2/*`、`/api/app/user/bankcard/*`）与新表 `cf_user_kyc_v2`、`cf_user_bank_card` 采集和管理用户的实名、实人核验、银行卡四要素绑卡数据，并 MUST 禁止在未经 KYC 通过且未完成绑卡的情况下放行授信开通与借款受理链路。

旧 `cf_user_kyc.step_status` 语义已废弃；旧 `paymentMethod/paymentAccount` 字段 MUST NOT 继续作为 KYC 通过条件。用户画像中的「月收入、居住地、职业」等字段 MUST 从 KYC 主路径剥离，归入独立的「用户画像补全」流程，MUST NOT 作为 `kyc_passed=1` 的判定条件。

#### Scenario: 更新画像字段

- **WHEN** 运营或系统任务更新用户画像字段
- **THEN** 系统 MUST 写入审计日志包含变更前后摘要（不含敏感明文）

#### Scenario: 用户完成新的 KYC + 绑卡

- **WHEN** 用户依次通过 step1（二要素 + 准入闸门）、step2（人脸实人核验），且随后完成银行卡四要素绑卡
- **THEN** 系统 MUST 在 `cf_user_kyc_v2.kyc_passed=1`、`cf_user_bank_card.status=VERIFIED ∧ is_primary=1`；MUST NOT 再使用 `cf_user_kyc.step_status` 作为判定字段

#### Scenario: 旧字段不再用于 KYC 判定

- **WHEN** 用户已填写旧画像字段（月收入/居住地/职业）但未完成 step1/step2
- **THEN** 系统 MUST 视为「KYC 未通过」；MUST NOT 因画像字段齐全而放行下游

### Requirement: KYC v2 与绑卡的对外 API 契约

用户服务 MUST 对外暴露以下 API（统一走 APISIX 边缘网关，不对未登录用户开放）：

- `POST /api/app/user/kyc/v2/step1` — 二要素 + 准入闸门，入参 `realName / idCardNo / Idempotency-Key`
- `POST /api/app/user/kyc/v2/step2` — 人脸实人核验受理，入参 `activeFaceToken / Idempotency-Key`
- `GET /api/app/user/kyc/v2/status` — 查询当前 KYC 综合状态（含 `eligibility_status / realname_status / face_status / kyc_passed`）
- `POST /api/app/user/bankcard/bind` — 四要素绑卡（入参不含 `realName / idCardNo`，由服务端从 `cf_user_kyc_v2` 读取）
- `GET /api/app/user/bankcard/list` — 查询用户绑卡列表（脱敏）
- `POST /api/app/user/bankcard/set-primary` — 切换主卡
- `POST /api/app/user/bankcard/unbind` — 解绑

旧 `POST /api/app/user/kyc/step1|step2|step3` MUST 进入 deprecation 期：在 `crediflow.kyc.use-v2=true` 时 MUST 直接返回 HTTP 410 Gone 或业务约定的「请使用 KYC v2」语义（含专用错误码）；在应急回滚为 `false` 时方可继续工作。

#### Scenario: 旧接口已废弃

- **WHEN** 客户端访问旧 `/api/app/user/kyc/step3` 且 `use-v2=true`
- **THEN** 系统 MUST 返回 410 Gone 或业务约定的废弃语义；MUST NOT 修改任何用户数据

#### Scenario: 绑卡请求不再接受身份字段

- **WHEN** 客户端在 `/api/app/user/bankcard/bind` 请求体中传入 `realName` 或 `idCardNo`
- **THEN** 系统 MUST 忽略前端字段并以 `cf_user_kyc_v2` 中已 VERIFIED 的实名为准；MUST NOT 因前端值不一致拒绝（除非数据库内不存在已 VERIFIED 的实名）

### Requirement: 人脸异步回调与内部接口

用户服务 MUST 对外（受边缘网关 / IP 白名单保护）暴露 `POST /api/internal/face-verify/callback`，由人脸厂商异步通知终态；该接口 MUST：

- 调用 `FaceVerifyProvider.verifySignature` 验签厂商签名
- 以 `(providerId, providerTxnNo)` 维度做幂等
- 写 `cf_face_verify_log` 流水
- 更新 `cf_user_kyc_v2.face_status` 与 `kyc_passed`
- 必要时投递 `KYC_PASSED_EVENT`

用户服务 MUST 同时对内（受 `internal-api-security` 内网签名保护）暴露：

- `POST /api/internal/risk/kyc/lookup-by-fingerprint`（可选）— 给风控服务按 `id_card_fingerprint` 反查 `userId / kyc_passed` 的轻量接口（MUST NOT 反查明文身份证号）
- `GET /api/internal/user/eligibility` — 返回 `{kycPassed, hasPrimaryBankCard}`

#### Scenario: 人脸回调验签

- **WHEN** 厂商 POST 回调到达 `/api/internal/face-verify/callback`
- **THEN** 系统 MUST 在调用任何业务逻辑前完成 `FaceVerifyProvider.verifySignature`，验签失败 MUST 返回 401 并写安全审计

#### Scenario: 风控反查不返回明文

- **WHEN** 风控服务用 `id_card_fingerprint` 反查 `userId`
- **THEN** 响应 MUST 仅含 `userId`、`kycPassed` 与脱敏掩码；MUST NOT 包含明文姓名或身份证号

### Requirement: 非生产 KYC 后门接口

用户服务 MAY 暴露 `POST /api/internal/test/kyc/force-pass` 用于非生产联调，直接把指定 `userId` 置为 `kyc_passed=1`。该接口 MUST 同时满足：

- 标注 `@Inner`（仅内网可达）
- 标注 `@Profile("!prod")` 或等价非生产限定
- 走 `internal-api-security` 内网签名拦截
- 接口调用 MUST 写一条带 `channel=BACKDOOR` 的 `cf_face_verify_log` 流水以便对账

#### Scenario: 生产环境无后门

- **WHEN** Spring 启动于生产 profile
- **THEN** 后门接口 MUST 不被注册到 Spring 上下文；外部请求该路径 MUST 返回 404
