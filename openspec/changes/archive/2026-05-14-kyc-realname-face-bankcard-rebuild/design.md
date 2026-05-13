# 设计文档：KYC 实名 + 实人 + 四要素绑卡能力重做

> 本文档 = D1~D7 决策的可执行视图 + 关键架构图 + 测试与回滚策略。
> 任何后续编码 / 联调 MUST 以本文为准；与 proposal 冲突时以本文档的「最终汇总」章节为准。

## 0. 最终汇总（一句话版本）

> KYC = 实名（二要素 + 准入闸门）+ 实人（人脸活体 + 公安比对，端到端单厂商，异步回调）；
> 绑卡是独立能力（四要素，独立 Provider，下沉 user-service）；
> 授信开通门槛 = KYC 通过 ∧ 已绑卡；
> 数据：新表 `cf_user_kyc_v2` + Flyway 迁移，旧表保留 90 天只读；
> 测试环境用 Nacos `kyc.face.verify.mock=true` + 白名单 + 后台后门跳过人脸，生产强断言禁止 Mock。

## 1. 整体流程（用户视角）

```
注册登录 ──► [KYC v2]                                            ──► [绑卡 v2]                  ──► [授信] ──► [借款]
            ├─ step1: 二要素 + 准入闸门                              ├─ 真实姓名 + 身份证号
            │  · 姓名/身份证 格式 + 校验位                            │  + 银行卡号 + 预留手机号
            │  · 18–55 年龄                                          │  · BankCardFourElementsProvider
            │  · 身份证唯一性（一人一证一账号）                       │  · 同一人四要素一致性
            │  · 黑名单（本地 + 风控服务）                            │  · 落 cf_user_bank_card
            │  · 限流防撞库                                          │  · 仅对外暴露 bindCardId
            ├─ step2: 人脸实人核验
            │  · FaceVerifyProvider（厂商端到端）
            │  · 前端 SDK 活体（眨眼/转头/张嘴随机）
            │  · 厂商内部完成活体 + 公安人像底照比对
            │  · 异步回调 /api/internal/face-verify/callback
            │  · 验签 + 幂等 + Redis 中间态
            └─ KYC 通过：realname_status=VERIFIED ∧ face_verified=true
                                                                       授信门槛：KYC ∧ 绑卡
```

## 2. 数据模型（D6）

### 2.1 新表

```sql
-- cf_user_kyc_v2：KYC 的事实表，幂等单条用户一行
CREATE TABLE cf_user_kyc_v2 (
  id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id             BIGINT       NOT NULL UNIQUE,
  real_name           VARCHAR(64)  NULL,
  id_card_no          VARBINARY(255) NULL  COMMENT 'AES 密文，CryptoTypeHandler 处理',
  id_card_mask        VARCHAR(32)  NULL    COMMENT '110***********0011',
  id_card_fingerprint VARCHAR(64)  NULL    COMMENT 'HMAC-SHA256(salt, name+idCardNo) 用于唯一性 + 幂等',
  age_at_submit       SMALLINT     NULL    COMMENT '提交瞬间从身份证算出',
  -- 准入闸门
  eligibility_status   VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED'
                         COMMENT 'NOT_SUBMITTED / PASS / REJECTED_AGE / REJECTED_DUP / REJECTED_BLACKLIST',
  eligibility_decided_at TIMESTAMP NULL,
  -- 实名（二要素）
  realname_status     VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED'
                         COMMENT 'NOT_SUBMITTED / PROCESSING / VERIFIED / FAILED',
  realname_provider_txn_no VARCHAR(64) NULL,
  realname_verified_at TIMESTAMP   NULL,
  -- 实人（人脸）
  face_status         VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED'
                         COMMENT 'NOT_SUBMITTED / PROCESSING / VERIFIED / FAILED',
  face_provider_id    VARCHAR(64) NULL    COMMENT '当时使用的 FaceVerifyProvider 标识',
  face_provider_biz_no VARCHAR(128) NULL  COMMENT '厂商业务单号 / 回调对单',
  face_provider_txn_no VARCHAR(128) NULL  COMMENT '厂商终态流水号',
  face_verified_at    TIMESTAMP   NULL,
  face_failure_code   VARCHAR(64) NULL    COMMENT '内部原因码（不外传）',
  -- 综合
  kyc_passed          TINYINT(1)  NOT NULL DEFAULT 0
                         COMMENT '= realname_status=VERIFIED ∧ face_status=VERIFIED',
  kyc_passed_at       TIMESTAMP   NULL,
  created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_id_card_fingerprint (id_card_fingerprint),  -- 一人一证
  KEY idx_kyc_passed (kyc_passed),
  KEY idx_realname_status (realname_status),
  KEY idx_face_status (face_status)
) COMMENT='KYC v2 事实表';

-- cf_user_bank_card：绑卡事实表（一个用户可有多张卡，但一次仅一张「主卡」）
CREATE TABLE cf_user_bank_card (
  id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id             BIGINT       NOT NULL,
  bind_card_id        VARCHAR(64)  NOT NULL UNIQUE COMMENT '对外脱敏 token，下游 fund-channel-gateway 持有此 ID',
  bank_code           VARCHAR(32)  NULL,
  card_no             VARBINARY(255) NULL COMMENT 'AES 密文',
  card_no_mask        VARCHAR(32)  NULL    COMMENT '6225**********1234',
  reserved_phone      VARBINARY(255) NULL COMMENT 'AES 密文',
  reserved_phone_mask VARCHAR(16)  NULL,
  card_no_fingerprint VARCHAR(64)  NOT NULL COMMENT '同卡防重',
  status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                         COMMENT 'PENDING / VERIFIED / FAILED / UNBOUND',
  is_primary          TINYINT(1)   NOT NULL DEFAULT 0,
  provider_id         VARCHAR(64)  NULL,
  provider_txn_no     VARCHAR(128) NULL,
  verified_at         TIMESTAMP    NULL,
  unbound_at          TIMESTAMP    NULL,
  created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_card_fp (user_id, card_no_fingerprint),
  KEY idx_user_primary (user_id, is_primary, status)
) COMMENT='银行卡四要素绑卡';

-- cf_id_card_blacklist：本地黑名单（运营维护）
CREATE TABLE cf_id_card_blacklist (
  id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card_fingerprint VARCHAR(64)  NOT NULL UNIQUE,
  reason_code         VARCHAR(64)  NOT NULL,
  reason_desc         VARCHAR(255) NULL,
  operator            VARCHAR(64)  NULL,
  created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='身份证黑名单（指纹存储，不存明文）';

-- cf_face_verify_log：人脸核验流水（用于回调幂等 + 对账）
CREATE TABLE cf_face_verify_log (
  id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id             BIGINT       NOT NULL,
  provider_id         VARCHAR(64)  NOT NULL,
  provider_biz_no     VARCHAR(128) NOT NULL UNIQUE COMMENT '我方下发的业务单号',
  provider_txn_no     VARCHAR(128) NULL,
  status              VARCHAR(16)  NOT NULL COMMENT 'PROCESSING / SUCCESS / FAILED',
  failure_code        VARCHAR(64)  NULL,
  failure_reason_internal VARCHAR(255) NULL,
  payload_digest      VARCHAR(64)  NULL,
  callback_received_at TIMESTAMP   NULL,
  duration_ms         INT          NULL,
  channel             VARCHAR(16)  NOT NULL COMMENT 'MOCK / WHITELIST / HTTP',
  created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id),
  KEY idx_provider_txn (provider_id, provider_txn_no)
) COMMENT='人脸核验流水';
```

### 2.2 旧表处理（D6）

- `cf_user_kyc`：**保留 90 天只读**。Flyway 同迁移内：
  - 把 `realname_status=VERIFIED` 的用户数据通过指纹映射搬到 `cf_user_kyc_v2`（实名沿用，人脸状态置 `NOT_SUBMITTED`，等待用户重新做人脸）
  - **不要把旧 step3 的 `payment_method/payment_account` 搬进 `cf_user_bank_card`**（旧字段不含卡号，无法满足四要素）
- 90 天后再单独发一个 cleanup change 删除旧表。

## 3. 模块结构（`java/user-service`）

```
com.crediflow.user
├── kyc/                          # 新 KYC 主流程
│   ├── controller/UserKycV2Controller.java         # /api/app/user/kyc/v2/*
│   ├── controller/InternalKycCallbackController.java # /api/internal/face-verify/callback
│   ├── controller/InternalKycTestController.java   # 后门接口（@Profile 限非生产）
│   ├── service/KycService.java
│   ├── service/impl/KycServiceImpl.java
│   ├── entity/UserKycV2.java
│   ├── mapper/UserKycV2Mapper.java
│   └── dto/*
├── eligibility/                  # 准入闸门
│   ├── EligibilityChecker.java
│   ├── AgeRangePolicy.java       # 18–55
│   ├── IdCardUniquenessPolicy.java
│   ├── BlacklistPolicy.java      # 本地 + 风控双层
│   ├── RealnameRateLimiter.java  # 复用现有实现
│   └── entity/IdCardBlacklist.java
├── face/                         # 人脸实人
│   ├── FaceVerifyProvider.java                # 顶层接口
│   ├── provider/MockFaceVerifyProvider.java   # 默认占位
│   ├── provider/HttpFaceVerifyProvider.java   # 模板化 HTTP（与 RealnameProvider 同套路）
│   ├── provider/RoutingFaceVerifyProvider.java
│   ├── service/FaceVerificationService.java
│   ├── callback/FaceCallbackVerifier.java
│   ├── config/FaceVerifyProperties.java
│   ├── support/FaceIdempotencyStore.java
│   ├── env/FaceMockSafetyInitializer.java     # 生产禁 Mock 启动断言
│   └── entity/FaceVerifyLog.java
├── bankcard/                     # 四要素绑卡（独立模块）
│   ├── BankCardFourElementsProvider.java      # 顶层接口
│   ├── provider/MockBankCardProvider.java
│   ├── provider/HttpBankCardProvider.java
│   ├── service/BankCardBindingService.java
│   ├── config/BankCardProperties.java
│   ├── entity/UserBankCard.java
│   └── controller/UserBankCardController.java # /api/app/user/bankcard/*
└── realname/                     # 旧 RealnameProvider 复用，但只作为 step1 内部子能力
```

## 4. 关键流程

### 4.1 二要素 + 准入闸门 (`POST /api/app/user/kyc/v2/step1`)

```
入参：realName, idCardNo, Idempotency-Key
1. 入参合法 & 18 位校验位
2. RealnameRateLimiter.tryAcquire(userId)
3. IdCardValidator → 算 age；若 age ∉ [18, 55] → eligibility_status=REJECTED_AGE，返回业务错误
4. 算 id_card_fingerprint = HMAC(salt, real_name + id_card_no)
5. 黑名单：先查 cf_id_card_blacklist（指纹）；命中 → REJECTED_BLACKLIST。
   未命中 → Feign 调 credit-risk-service /api/internal/risk/blacklist/check（传指纹，不传明文）
6. 唯一性：select 1 from cf_user_kyc_v2 where id_card_fingerprint = ? and user_id != ?
   命中 → REJECTED_DUP
7. 调 RealnameProvider（与旧实现完全一致：二要素一致性，retryable/terminal/success 三态）
8. 落 cf_user_kyc_v2：realname_status, age_at_submit, id_card_*；eligibility_status=PASS
9. 不直接进 face_status=PROCESSING；等用户主动触发 step2 后才进 PROCESSING
```

### 4.2 人脸实人核验 (`POST /api/app/user/kyc/v2/step2`)

```
入参：activeFaceToken（前端 SDK 完成活体后拿到的厂商 token）, Idempotency-Key

非生产 Mock 模式（Nacos kyc.face.verify.mock=true）：
  · 直接落 cf_face_verify_log channel=MOCK status=SUCCESS
  · 落 cf_user_kyc_v2.face_status=VERIFIED, face_verified_at=now
  · 同步返回 KYC 通过
  · 不调任何厂商 API、不调摄像头

非生产白名单模式（kyc.face.verify.whitelist 命中手机号或身份证指纹）：
  · 同 Mock 路径，channel=WHITELIST

生产 / 测试 真实模式：
  1. 生成 provider_biz_no（雪花 ID）
  2. 落 cf_face_verify_log status=PROCESSING channel=HTTP
  3. 调 FaceVerifyProvider.submit(userId, activeFaceToken, biz_no)
     · Provider 内部完成活体确认 + 公安底照比对
     · 同步返回受理态（PROCESSING + 厂商单号）
  4. cf_user_kyc_v2.face_status=PROCESSING, face_provider_biz_no=...
  5. 中间态写 Redis：face:state:<userId>:<bizNo> TTL 30min（前端轮询查询）
  6. 等厂商异步回调 /api/internal/face-verify/callback
```

### 4.3 人脸异步回调 (`POST /api/internal/face-verify/callback`)

```
1. 验签：厂商签名 + 我方 internal-api-security 内网签名（双层）
2. 解析 payload，拿到 provider_txn_no, biz_no, terminal=SUCCESS/FAILED
3. 幂等：Redis SETNX face:cb:<provider_id>:<provider_txn_no> TTL 24h；命中 → 200 ACK
4. 加 payload_digest 入 cf_face_verify_log
5. 按 biz_no 找回 cf_user_kyc_v2 用户
6. 写入：face_status=VERIFIED/FAILED, face_provider_txn_no, face_failure_code
7. 若 realname_status=VERIFIED ∧ face_status=VERIFIED：
     kyc_passed=1, kyc_passed_at=now
     发 RocketMQ 事件 KYC_PASSED_EVENT（供 credit/loan 等下游订阅）
8. 不主动推送给前端，前端自行轮询 /api/app/user/kyc/v2/status
```

### 4.4 四要素绑卡 (`POST /api/app/user/bankcard/bind`)

```
入参：cardNo, bankCode, reservedPhone, Idempotency-Key
0. 必须 kyc_passed=true 才允许进入此接口；否则 403
1. 比对：cardNo 持卡人 = cf_user_kyc_v2.real_name + id_card_no
   （后端从已落库的实名取值，前端不再传姓名身份证）
2. 调 BankCardFourElementsProvider.verify(name, idCardNo, cardNo, reservedPhone)
3. SUCCESS：落 cf_user_bank_card，生成 bind_card_id（UUID），状态 VERIFIED
4. FAILED：落 status=FAILED；不抛敏感失败原因给前端
5. 设为主卡（is_primary=1）；同用户已有主卡时先 UNBOUND 旧的
6. 对外仅返回 bind_card_id + card_no_mask + bank_code
```

## 5. FaceVerifyProvider 抽象（D2 + D4）

```java
public interface FaceVerifyProvider {
    SubmitReceipt submit(FaceVerifyCommand cmd);   // 同步受理，返回受理号 + 厂商业务单号
    CallbackParseResult parseCallback(byte[] raw, Map<String,String> headers);
    boolean verifySignature(byte[] raw, Map<String,String> headers);
    String providerId();
}
```

- 实现：`MockFaceVerifyProvider`（默认非生产）、`HttpFaceVerifyProvider`（模板化 HTTP + Nacos 注入 baseUrl/appKey/appSecret/sign 策略）
- 路由：`RoutingFaceVerifyProvider` 按 Nacos `kyc.face.provider.active=mock|http|<vendorId>` 切换
- 与 `RealnameProvider` 完全相同的工程套路：可插拔签名策略 (`FaceSignatureStrategy`：`HmacSha256` / `Groovy` / `NoOp`)、模板化请求体、可插拔结果解析

## 6. BankCardFourElementsProvider 抽象（D5）

```java
public interface BankCardFourElementsProvider {
    BankCardVerifyResult verify(BankCardVerifyCommand cmd);
    String providerId();
}
```

- 实现：`MockBankCardProvider`（非生产）、`HttpBankCardProvider`（如银联鉴权、支付宝四要素接口）
- 同 Provider 抽象 + Nacos 切换 + 生产禁 Mock 强断言
- 与 `fund-channel-gateway` **完全隔离**：网关持有的 `bindCardId` 仅是这里生成的脱敏 token，网关不会、也不可以反查明文卡号

## 7. 测试策略（Mock 三件套）

| 模式 | 触发 | 行为 | 适用环境 |
| --- | --- | --- | --- |
| **Nacos 全局 Mock** | `kyc.face.verify.mock=true` | 不唤起 SDK、不调摄像头、人脸自动 SUCCESS、走完整状态机 | 仅 dev / test |
| **白名单跳过** | 手机号或身份证指纹命中 `kyc.face.verify.whitelist` | 同 Mock，但只对个别测试账号生效 | dev / test / 极少数预生产烟囱测试 |
| **后台手动干预** | `/api/internal/test/kyc/force-pass` (`@Profile("!prod")`) | 直接把指定 userId 改成 `kyc_passed=1` | dev / test |

### 7.1 生产强断言

- `FaceMockSafetyInitializer` 在 Spring 启动阶段读取 active profile，若属生产集合且 `kyc.face.verify.mock=true` → **抛异常中止启动**
- 同样的强断言移植到 `BankCardMockSafetyInitializer`
- 内部测试 Controller 必须 `@Profile("!prod") + @Inner`，双重保护

### 7.2 该测的点（不依赖真人脸）

1. **基础资料**：身份证位数 / 校验位 / 特殊字符 / 空姓名
2. **18–55 边界**：17 岁、18 岁、55 岁、56 岁
3. **一人一证**：同身份证不同 userId 二次提交
4. **黑名单**：本地命中 / 本地未命中风控命中 / 双层都不命中
5. **未 KYC 强进绑卡 / 授信 / 借款**：必须 403
6. **状态流转**：NOT_SUBMITTED → PROCESSING → VERIFIED；任何错误路径都不能错误升级
7. **回调幂等**：相同 provider_txn_no 重复回调不重复落库、不重复发 MQ
8. **回调验签**：错签 / 时间漂移 / 未带签名 全部 401
9. **限流**：单用户超限直接拒绝外呼

### 7.3 生产冒烟

- 真机刷脸 1–2 次：能拉起、能完活、能比对
- 不再追求异常覆盖；异常路径在测试环境用后门构造

## 8. 与其它能力的边界

- **credit-application-lifecycle / microservice-loan-application**：受理前置统一查 `cf_user_kyc_v2.kyc_passed = 1` 且 `cf_user_bank_card` 至少有一条 `VERIFIED + is_primary=1`。否则 422 拒绝。
- **fund-channel-gateway**：完全不感知用户实名 / 人脸；只通过 `bindCardId` 引用绑卡，由网关内部解密成实际卡号外呼资金方。本 change 不修改网关代码，仅依赖其 `bindCardId` 契约。
- **internal-api-security**：新增 `/api/internal/face-verify/callback` 受 `X-Internal-Sign + X-Timestamp` 保护；厂商 → 我方的回调走厂商自己的签名规范（Provider.verifySignature），不与内网签名混用。
- **credit-risk-service**：暴露 `/api/internal/risk/blacklist/check`（POST `{idCardFingerprint}` → `{hit:boolean, reasonCode:string}`），用 `@Inner` + 内网签名保护。

## 9. 回滚开关

| 开关 | 默认 | 应急回滚行为 |
| --- | --- | --- |
| `crediflow.kyc.use-v2` | true | 设 false → 流量切回旧 step1/2/3（旧表只读 90 天，可读不可写） |
| `crediflow.kyc.face.required` | true | 设 false → KYC 通过条件降级为 `realname_status=VERIFIED`（仅限重大事故） |
| `crediflow.credit.require-bankcard` | true | 设 false → 授信门槛降级为只要 KYC 通过（仅限重大事故） |
| `kyc.face.verify.mock` | false（prod 必为 false） | 仅非生产可置 true 跳过人脸 |
| `crediflow.kyc.eligibility.age.min` / `.max` | 18 / 55 | Nacos 动态调整 |

## 10. 已锁定的 Open Questions（不再讨论）

- D1~D7 全部见 `proposal.md` 与本文 §0。
- 人脸厂商选型（腾讯 / 阿里 / 旷视 / 商汤）放到独立 change `kyc-face-provider-<vendor>`，本 change 只交付 Mock + HTTP 占位框架。
- 旧表 90 天后清理放到独立 change `kyc-legacy-cleanup`。
- 前端 UI 实现（活体动作交互、错误提示文案）由前端仓库的 PR 承接，本 change 仅锁定后端契约与状态机。
