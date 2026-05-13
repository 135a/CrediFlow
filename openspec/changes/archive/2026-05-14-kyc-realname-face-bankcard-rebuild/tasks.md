# Tasks: KYC 实名 + 实人 + 四要素绑卡能力重做

> 严格分批，禁止跨阶段并发开工。每个阶段交付后再开下一阶段。
> 默认所有新代码以 `crediflow.kyc.use-v2` 开关包裹，初始全量发布时为 `true`，应急回滚切 `false`。

## 1. Phase 0：契约 / 数据 / 抽象骨架（无业务逻辑）

- [x] 1.1 锁定 `cf_user_kyc_v2`、`cf_user_bank_card`、`cf_id_card_blacklist`、`cf_face_verify_log` 表结构 DDL（见 design §2.1）；编写 Flyway `V?_kyc_v2.sql`（不含数据搬运）
- [x] 1.2 在 `crediflow-common` 新增脱敏工具：`IdCardFingerprintCalculator`、`MaskingUtil`（卡号 / 手机号 / 身份证）；与已有 `RealnameVerificationService` 中算法保持一致
- [x] 1.3 在 `crediflow-common` 新增领域事件 DTO：`KycPassedEvent`（字段：`userId / realnameProviderTxnNo / faceProviderTxnNo / passedAt / idCardMask`）+ `MqConstants.TOPIC_KYC_PASSED`
- [x] 1.4 在 `user-service` 创建包结构：`kyc/`、`eligibility/`、`face/`、`bankcard/`（见 design §3）
- [x] 1.5 定义抽象接口（仅签名，不实现）
  - `FaceVerifyProvider`（`submit / verifySignature / parseCallback / providerId`）
  - `BankCardFourElementsProvider`（`verify / providerId`）
  - `EligibilityChecker`（聚合 `AgeRangePolicy / IdCardUniquenessPolicy / BlacklistPolicy / RealnameRateLimiter`）
- [x] 1.6 新增 `cf_user_kyc_v2` / `cf_user_bank_card` / `cf_id_card_blacklist` / `cf_face_verify_log` 的 Entity + Mapper + Repository，不暴露 Service

## 2. Phase 1：准入闸门 + 二要素降级（旧实名能力归位）

- [x] 2.1 实现 `AgeRangePolicy`：身份证号 → 出生日期 → 年龄；区间 `[kyc.eligibility.age.min, kyc.eligibility.age.max]` 默认 `[18,55]`
- [x] 2.2 实现 `IdCardUniquenessPolicy`：基于 `id_card_fingerprint` 唯一索引；幂等场景白名单同 `userId`
- [x] 2.3 实现 `BlacklistPolicy` 双层校验
  - 本地：`cf_id_card_blacklist` 按指纹查询
  - 风控：Feign client → `credit-risk-service` `/api/internal/risk/blacklist/check`（暂占位 stub，留 `credit-risk-service` 后续提供实现）
- [x] 2.4 复用现有 `RealnameRateLimiter`（来自旧 user-realname-verification），抽到 `eligibility/` 包内
- [x] 2.5 实现 `EligibilityChecker.check(userId, realName, idCardNo)`：依次执行 1–4 步，结果写 `cf_user_kyc_v2.eligibility_status`；拒绝原因外传统一文案
- [x] 2.6 新 `KycServiceImpl.step1(...)`：闸门通过 → 调用复用的 `RealnameVerificationService`（保留旧三态语义）→ 写 `realname_status / realname_provider_txn_no / realname_verified_at`
- [x] 2.7 实现 `credit-risk-service` 侧的 `/api/internal/risk/blacklist/check` 内部接口（如不在本仓库则记 TODO 拉新 change）

## 3. Phase 2：人脸实人核验链路（Mock 先行）

- [x] 3.1 `FaceVerifyProperties`（Nacos 注入）：`active / mock / whitelist / providers.{id}.{baseUrl,appKey,appSecret,timeout,signStrategy,templates}`
- [x] 3.2 `MockFaceVerifyProvider`：同步受理立即返回受理号；不发起任何外呼；channel=MOCK
- [x] 3.3 `HttpFaceVerifyProvider`：模板化请求体 + 可插拔签名策略（`FaceSignatureStrategy`：`HmacSha256 / Groovy / NoOp`，与旧 `RealnameProvider` 同套路）；同步响应仅返回受理态
  - 当前为接入骨架（占位 submit / verifySignature 默认拒绝）；真实厂商接入由独立 change `kyc-face-provider-<vendor>` 完成
- [x] 3.4 `RoutingFaceVerifyProvider`：按 Nacos `kyc.face.provider.active` 委派
- [x] 3.5 `FaceMockSafetyInitializer`：生产 profile + `mock=true` → 抛错中止启动
- [x] 3.6 `FaceVerificationService.submit(userId, activeFaceToken, idempotencyKey)`：
  - Nacos `kyc.face.verify.mock=true` 或白名单命中 → 走 Mock 路径（直接 SUCCESS，channel=MOCK / WHITELIST）
  - 否则调 Provider，落 `cf_face_verify_log status=PROCESSING channel=HTTP`，置 `face_status=PROCESSING`，Redis 写 `face:state:<userId>:<bizNo>` TTL 30min
- [x] 3.7 `InternalKycCallbackController.faceCallback(...)`：
  - 双层验签（`FaceVerifyProvider.verifySignature` + 内网约束）
  - Redis SETNX 幂等 `face:cb:<providerId>:<providerTxnNo>` TTL 24h
  - 更新 `cf_face_verify_log` + `cf_user_kyc_v2.face_status`
  - 若 `realname_status=VERIFIED ∧ face_status=VERIFIED`：`kyc_passed=1` + 投 `KYC_PASSED_EVENT`
- [x] 3.8 非生产后门 `InternalKycTestController.forcePass(userId)`：`@Profile("!prod") + @Inner`；写 `channel=BACKDOOR` 流水

## 4. Phase 3：KYC v2 对外 API + 状态机

- [x] 4.1 `UserKycV2Controller`：
  - `POST /api/app/user/kyc/v2/step1`
  - `POST /api/app/user/kyc/v2/step2`
  - `GET  /api/app/user/kyc/v2/status`
- [x] 4.2 旧 `UserKycController` 的 step1/2/3 增加开关 `crediflow.kyc.use-v2`：默认 true → 返回 `KYC_LEGACY_API_GONE` 与「请使用 KYC v2」语义
- [x] 4.3 内部反查接口 `GET /api/internal/user/eligibility`：返回 `{kycPassed, hasPrimaryBankCard}`；走 `internal-api-security`
- [ ] 4.4 端到端冒烟（Mock 模式）：step1 → step2(mock SUCCESS) → status → KYC_PASSED_EVENT 投递

## 5. Phase 4：银行卡四要素绑卡（独立链路）

- [x] 5.1 `BankCardProperties`（Nacos 注入）+ `MockBankCardProvider` + `HttpBankCardProvider` + 路由 + `BankCardMockSafetyInitializer`（生产禁 Mock）
- [x] 5.2 `BankCardBindingService.bind(userId, cardNo, reservedPhone, bankCode, idempotencyKey)`：
  - 校验 `cf_user_kyc_v2.kyc_passed=1`（否则 403）
  - 从服务端读取已 VERIFIED 的 `realName / idCardNo`；忽略前端传入的姓名 / 身份证
  - 调 Provider 同步校验四要素
  - 落 `cf_user_bank_card`，生成 `bindCardId`（UUID）；同卡防重 `(user_id, card_no_fingerprint)`
  - 设为新主卡：旧主卡 `is_primary=0`
- [x] 5.3 `UserBankCardController`：
  - `POST /api/app/user/bankcard/bind`
  - `GET  /api/app/user/bankcard/list`（仅 mask + bindCardId）
  - `POST /api/app/user/bankcard/set-primary`
  - `POST /api/app/user/bankcard/unbind`
- [x] 5.4 在 `internal-api-security` 的 `InternalAuthFilter` 引入 `crediflow.internal.public-paths` 路径白名单（默认含人脸厂商回调），保留 `X-Internal-Sign` 对反查接口的拦截不变

## 6. Phase 5：下游服务前置校验改造

- [x] 6.1 `credit-application-service` 受理前置：
  - 调 `/api/internal/user/eligibility`，判断 `kycPassed ∧ hasPrimaryBankCard`
  - 任一不满足 → 拒绝；不再读 `cf_user_kyc.step_status`
- [x] 6.2 `loan-application-service` 受理前置：
  - 同样调 `/api/internal/user/eligibility`
  - 后续放款受理 body 收款卡字段 MUST 用 `bindCardId`（在资金网关接入 change 中已落地，本 change 不再重复改造）
- [ ] 6.3 后台 / Admin 视图同步：`cf_user_kyc_v2` 综合状态展示，旧表数据视图仍可只读查询用于对账（本仓库无 system-admin-service，前端 admin 仓承接，留独立 PR）

## 7. Phase 6：数据迁移（Flyway）

- [x] 7.1 Flyway `V6__kyc_v2_backfill.sql`：把 `cf_user_kyc.realname_status=VERIFIED` 的用户写入 `cf_user_kyc_v2`（`realname_status=VERIFIED`、`face_status=NOT_SUBMITTED`、`kyc_passed=0`）
- [x] 7.2 旧表 `cf_user_kyc` 改为应用层只读：旧 `UserKycController` step1/2/3 在 `use-v2=true` 时直接返回 `KYC_LEGACY_API_GONE`，`RealnameVerificationService` 不再被入站流量触达；保留 90 天 + DBA 备份在独立运维工单跟踪
- [x] 7.3 旧 step3 字段 `payment_method / payment_account` **不**搬运到 `cf_user_bank_card`；存量用户必须重新走绑卡（V6 backfill 已遵守）
- [x] 7.4 应急回滚 = Nacos 将 `crediflow.kyc.use-v2` 置 `false`，旧 Controller 立即恢复入口；不需要数据库回滚（v2 表保留即可）

## 8. Phase 7：测试与验收

- [ ] 8.1 单元测试：`AgeRangePolicy` 边界（17 / 18 / 55 / 56）、`IdCardUniquenessPolicy`、`BlacklistPolicy` 双层、`RealnameRateLimiter`
- [ ] 8.2 单元测试：`FaceVerificationService` Mock / 白名单 / HTTP 三路径；回调幂等；签名失败；中间态 Redis 读写
- [ ] 8.3 单元测试：`BankCardBindingService` 持卡人不一致 / 同卡重复 / 主卡切换 / 解绑
- [ ] 8.4 集成测试（dev profile）
  - 端到端：step1 → step2(mock) → status → 绑卡 → 授信 → 借款受理（受 mock fund-channel-gateway 拦截）
  - 异常：未 KYC 强进绑卡 / 授信 / 借款 → 全部 403
  - 年龄 17 / 56 → REJECTED_AGE
  - 同身份证不同账号 → REJECTED_DUP
- [ ] 8.5 安全测试：生产 profile 启动 + `kyc.face.verify.mock=true` → 应用启动失败
- [ ] 8.6 安全测试：人脸回调签名错误 / 时间戳漂移 / 重复回调；后门接口在 prod profile 下 404
- [x] 8.7 运行 `openspec change validate kyc-realname-face-bankcard-rebuild --strict` ✓ Pass

## 9. Phase 8：上线与回滚预案

- [ ] 9.1 Nacos 关键配置 review：`kyc.face.provider.active=mock`、`kyc.face.verify.mock=true`（仅 dev / test）、白名单清单、`age.min/max=18/55`
- [ ] 9.2 生产 Nacos 强制：`kyc.face.verify.mock=false`、`kyc.bankcard.provider.mock=false`
- [ ] 9.3 PR 描述：列出 **BREAKING** 项（user-kyc-authentication / user-realname-verification / microservice-user / credit-application-lifecycle / microservice-loan-application）+ 回滚开关位置（`crediflow.kyc.use-v2 / crediflow.kyc.face.required / crediflow.credit.require-bankcard`）
- [ ] 9.4 灰度计划：先内部白名单账号 → 5% → 50% → 100%；任何阶段异常立即把 `use-v2` 切回 false
- [ ] 9.5 旧表 cleanup 拆为独立 change `kyc-legacy-cleanup`，90 天后再发起
- [ ] 9.6 真实厂商接入拆为独立 change `kyc-face-provider-<vendor>` 与 `kyc-bankcard-provider-<vendor>`，本 change 不交付
