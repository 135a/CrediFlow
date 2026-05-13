# KYC 实名 + 实人 + 四要素绑卡能力重做（Rebuild）

## Why

现有 KYC 流程（`user-kyc-authentication` / `user-realname-verification`）只覆盖：

- step1：填基础信息（月收入、居住地、职业等画像字段）
- step2：姓名 + 身份证号二要素第三方一致性核验（`RealnameVerificationService`）
- step3：极简「支付账号绑定」—— 只把 `payment_method` / `payment_account` 落库，**不做真实银行卡四要素验证**，也**不与放款链路打通**

业务侧已经明确以下不可接受的合规与风险缺口：

1. **缺失「实人核验」**：仅二要素一致性证明「这个身份证属于这个姓名」，无法证明「正在操作的就是本人」。无活体、无防照片视频冒充、无公安底照人像比对。
2. **缺失准入闸门**：
   - 同一身份证号可以被多个账号反复绑定 → 撞库 / 黑产养号
   - 没有 **18–55 岁**合规区间限制 → 监管要求未落地
   - 没有身份证黑名单拦截 → 已知风险人员可重复申请
3. **绑卡链路形同虚设**：旧 step3 仅是字符串字段，不调用银行四要素鉴权，无法保证「卡号 + 预留手机号 + 身份证 + 姓名」属于同一人，下游放款无法把钱准确划到本人卡，违反「资金 MUST 由资金方对公账户直接划入用户绑定储蓄卡」的资金侧 SOP。
4. **职责边界混乱**：旧 step3 把「绑卡」当成 KYC 的一部分，但绑卡能力应独立可复用（用户换卡、换银行、补绑都不应触发整套 KYC 重做）；同时也未明确「开通授信」与「KYC 通过」「已绑卡」之间的依赖关系。

本次 change 把 KYC 拆为两块独立能力 + 一道闸门：

- **KYC = 二要素 + 准入闸门 + 人脸实人核验**（终态：`realname_status=VERIFIED` ∧ `face_verified=true`）
- **银行卡四要素绑卡**：与 KYC 解耦的独立 capability
- **授信开通门槛**：`KYC 通过 ∧ 已绑卡`，二者缺一不可

## What Changes

### 角色与边界（必须分清）

| 链路 | 调用关系 |
| --- | --- |
| 手机号注册 + 短信登录 | 已有，不变（前置条件） |
| KYC 二要素 + 准入闸门 | 用户 → `user-service`（本地黑名单 + 风控服务双层校验 + 18–55 + 身份证唯一） |
| 人脸实人核验 | 前端 SDK 调摄像头 + 活体 → `FaceVerifyProvider`（Nacos 配置驱动）→ 厂商端到端核身 → 异步回调 `POST /api/internal/face-verify/callback` |
| 银行卡四要素绑卡 | 用户 → `user-service` → `BankCardFourElementsProvider`（与人脸核身并列，**不**走 fund-channel-gateway）|
| 授信开通 / 借款放款 | 校验 KYC 通过 ∧ 已绑卡 → 允许进入 credit-application / loan-application 流程 |

### Capabilities

**New（全新能力）**

- `kyc-face-liveness` — 人脸实人核身（活体 + 公安底照比对，**端到端一次厂商调用**，异步回调）
- `bankcard-four-elements-binding` — 银行卡四要素绑卡（独立 Provider + token 化 `bindCardId`）
- `kyc-eligibility-gate` — 准入闸门（18–55 / 身份证唯一性 / 本地黑名单 + 风控服务双层 / 限流）

**Modified（BREAKING）**

- `user-kyc-authentication` **BREAKING** — 三步生命周期重写：
  - 旧 `step_status: 0/1/2/3` 语义彻底变更，新表 `cf_user_kyc_v2` 承载
  - 旧 step1（月收入/居住地等画像字段）从 KYC 主流程剥离（不再阻塞 KYC）
  - 旧 step3（payment_method/payment_account 字符串）废弃，由 `bankcard-four-elements-binding` 替代
- `user-realname-verification` **BREAKING** — 二要素从「KYC 终态判定」降级为「闸门前置」；终态由人脸实人核验决定
- `microservice-user` **BREAKING** — KYC API 路径与契约变更，新增 `/api/app/user/kyc/v2/*` 与 `/api/app/user/bankcard/*`，旧 `/api/app/user/kyc/step1|step2|step3` 进入 deprecation 期
- `credit-application-lifecycle` **BREAKING** — 授信开通门槛升级为 KYC 通过 ∧ 已绑卡
- `microservice-loan-application` **BREAKING** — 借款受理前置校验 KYC + 绑卡终态
- `internal-api-security` — 新增 `/api/internal/face-verify/callback`、`/api/internal/bankcard/callback`（如有）的验签与防重放约束

### 关键决策（与本次设计文档同步锁定，不再变更）

- **D1** KYC 通过 = 实名 + 实人（不含绑卡）。绑卡是 KYC 之后独立能力。开通授信 = KYC ∧ 绑卡
- **D2** 人脸：单一厂商端到端一次核身（活体 + 比对一气呵成）
- **D3** 异步回调为主（默认 A），保留双模兼容；回调 MUST 验签 + 幂等 + Redis 中间态
- **D4** `FaceVerifyProvider` 抽象 + Nacos 动态切换；先内置 Mock，真实厂商接入留独立 change
- **D5** `BankCardFourElementsProvider` 抽象，下沉到 `user-service`，**不**复用 fund-channel-gateway；落 `cf_user_bank_card`，对外仅暴露 `bindCardId`
- **D6** 数据迁移：新表 `cf_user_kyc_v2` + Flyway 版本化迁移；旧表 `cf_user_kyc` 保留 90 天只读
- **D7** 黑名单：本地 `cf_id_card_blacklist` 命中即拒，未命中再调 `credit-risk-service` 双层兜底

## Impact

- **Affected specs**：
  - New：`kyc-face-liveness`、`bankcard-four-elements-binding`、`kyc-eligibility-gate`
  - Modified（BREAKING）：`user-kyc-authentication`、`user-realname-verification`、`microservice-user`、`credit-application-lifecycle`、`microservice-loan-application`
  - Modified：`internal-api-security`
- **Affected code（计划）**：
  - `java/user-service`：新增 `face/`、`bankcard/`、`eligibility/` 包；KYC Controller v2；新 `cf_user_kyc_v2` / `cf_user_bank_card` / `cf_id_card_blacklist` / `cf_face_verify_log` 实体与 Mapper；Flyway V?_迁移
  - `java/credit-risk-service`：暴露 `/api/internal/risk/blacklist/check` 内部接口
  - `java/loan-application-service` / `java/credit-application-service`：受理前置校验 KYC + 绑卡终态
  - 前端（APP / 小程序）：人脸 SDK 接入 + 新 KYC UI + 绑卡 UI（本 change 仅约束契约，UI 实现在前端仓库）
- **配置中心（Nacos）**：
  - `kyc.face.verify.mock`、`kyc.face.verify.whitelist`（手机号/身份证白名单）
  - `kyc.eligibility.age.min/max`、`kyc.eligibility.rate-limit`
  - `kyc.face.provider.*`（baseUrl / appKey / appSecret / 模板 / 签名脚本）
  - `kyc.bankcard.provider.*`
- **测试环境策略**（写入设计 + 验收）：
  - Nacos `kyc.face.verify.mock=true` 全局跳过人脸
  - 白名单手机号 / 身份证免人脸
  - 后台后门接口手动改 KYC 终态（限非生产 profile）
- **生产强断言**：生产 profile 启动时检测 `mock=true` MUST 抛错中止；任何 KYC 资料一致但人脸未过的账户 MUST NOT 进入「KYC 通过」
- **回滚开关**：
  - `crediflow.kyc.use-v2`（默认 true，应急可切回旧 step1/2/3，旧表保留 90 天只读）
  - `crediflow.kyc.face.required`（默认 true；应急可禁用人脸要求，仅限重大事故）
  - `crediflow.credit.require-bankcard`（默认 true）
- **BREAKING 项**：见上方 Modified capabilities；下游服务（credit/loan）联调需要协同上线。
