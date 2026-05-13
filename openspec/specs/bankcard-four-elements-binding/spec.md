# bankcard-four-elements-binding Specification

## Purpose

定义银行卡四要素绑卡能力：通过抽象 `BankCardFourElementsProvider`，由第三方鉴权通道完成「真实姓名 + 身份证号 + 银行卡号 + 银行预留手机号」四要素的同一持卡人一致性校验；绑卡数据落 `cf_user_bank_card`，对外仅暴露脱敏 `bindCardId`。该能力**独立于 fund-channel-gateway**，下沉到 `user-service`；KYC 通过是进入本能力的前置条件。

## Requirements

### Requirement: 绑卡能力下沉 user-service 且独立于资金网关

系统 MUST 在 `user-service`（或等价用户域服务）内独立实现 `BankCardFourElementsProvider` 抽象与四要素绑卡主流程；MUST NOT 复用或调用 `fund-channel-gateway` 完成身份鉴权类四要素校验。资金网关持有的 `bindCardId` MUST 严格仅作为脱敏引用 token，由用户域负责生成与发放；资金网关在外呼资金方时再以 `bindCardId` 在内部还原真实卡号，并 MUST NOT 反查身份四要素。

#### Scenario: 资金网关不参与四要素校验

- **WHEN** 用户首次发起绑卡或更换主卡
- **THEN** 系统 MUST 由 `user-service` 调用 `BankCardFourElementsProvider` 完成校验；MUST NOT 经 `fund-channel-gateway` 的任何受理或回调接口

### Requirement: KYC 通过为绑卡前置条件

系统 MUST 在所有四要素绑卡接口入口校验当前用户 `kyc_passed=true`；未通过 KYC 的用户 MUST 拒绝进入绑卡流程并返回明确的「请先完成 KYC 实名实人核验」语义错误；MUST NOT 因绑卡接口暴露任何 KYC 内部状态字段或失败原因。

#### Scenario: 未 KYC 强进绑卡

- **WHEN** `kyc_passed=false` 的用户调用 `/api/app/user/bankcard/bind`
- **THEN** 系统 MUST 返回 403 或业务约定拒绝码；MUST NOT 调用 Provider；MUST NOT 落任何 `cf_user_bank_card` 记录

### Requirement: 同一持卡人四要素一致性

系统 MUST 在绑卡时把「真实姓名 + 身份证号」自服务端已落库的 `cf_user_kyc_v2`（VERIFIED 终态）读取，前端 MUST NOT 重复传姓名与身份证；用户只提交「银行卡号 + 预留手机号」。系统 MUST 把 4 项一起提交 Provider 校验，任何一项不匹配 MUST 视为终态失败；MUST NOT 允许跨用户 / 跨姓名 / 跨证件的绑卡。

#### Scenario: 卡片持卡人与 KYC 不一致

- **WHEN** 用户输入的银行卡持卡人姓名与 `cf_user_kyc_v2.real_name` 不一致
- **THEN** Provider MUST 返回失败终态；系统 MUST 写 `cf_user_bank_card.status=FAILED` 并返回业务可读摘要；MUST NOT 暴露厂商原始错误码

#### Scenario: 用户篡改前端身份信息

- **WHEN** 前端在请求体中重新塞入 `realName` 或 `idCardNo`
- **THEN** 系统 MUST 忽略前端值并以服务端 `cf_user_kyc_v2` 为准；MUST NOT 因前端绕过 KYC 数据来源完成绑卡

### Requirement: 卡片落库与脱敏发放

系统 MUST 将四要素校验通过的卡持久化到 `cf_user_bank_card`，至少包含：`user_id`、`bind_card_id`（UUID 或等价无规律 token，对外唯一）、`bank_code`、`card_no`（密文，AES，`CryptoTypeHandler`）、`card_no_mask`、`reserved_phone`（密文）、`reserved_phone_mask`、`card_no_fingerprint`（用于防重）、`status`、`is_primary`、`provider_id`、`provider_txn_no`、`verified_at`。对外接口 MUST 仅返回 `bind_card_id` + `card_no_mask` + `bank_code`；MUST NOT 返回卡号明文、预留手机号明文或厂商原始流水号。

#### Scenario: 绑卡成功对外响应

- **WHEN** 绑卡成功
- **THEN** API MUST 仅返回 `bindCardId` / `cardNoMask` / `bankCode`；MUST NOT 在 body 或 header 出现卡号明文或预留手机号明文

#### Scenario: 同卡重复绑定

- **WHEN** 同一用户使用相同 `card_no_fingerprint` 重复发起绑卡
- **THEN** 系统 MUST 拒绝重复绑定或返回与上次一致的 `bindCardId`（幂等）；MUST NOT 在 `cf_user_bank_card` 写出第二条 VERIFIED 记录

### Requirement: 主卡切换与解绑

系统 MUST 支持「设置主卡」与「解绑」操作；同一用户在同一时刻 MUST 仅有一张 `status=VERIFIED ∧ is_primary=1` 的卡，用作下游资金通道使用。设为新主卡时 MUST 把旧主卡 `is_primary=0`；解绑 MUST 将卡状态置 `UNBOUND` 并记录 `unbound_at`，MUST NOT 物理删除历史卡数据。

#### Scenario: 切换主卡

- **WHEN** 用户在多张已 VERIFIED 卡中切换主卡
- **THEN** 系统 MUST 在同一事务内把目标卡 `is_primary=1`、其它 VERIFIED 卡 `is_primary=0`

#### Scenario: 主卡解绑后授信链路兜底

- **WHEN** 用户解绑当前唯一主卡
- **THEN** 系统 MUST 把该卡置 `UNBOUND`；下游借款受理 MUST 因「无可用主卡」拒绝；MUST NOT 静默使用旧 `bindCardId`

### Requirement: Provider 抽象、Nacos 配置与 Mock 安全

系统 MUST 通过统一接口 `BankCardFourElementsProvider`（或等价命名）抽象第三方四要素鉴权；MUST 通过 Nacos 注入 `baseUrl`、`appKey`、`appSecret`、签名策略、超时等参数；MUST 内置 Mock 实现用于非生产联调。系统 MUST 在 Spring 启动阶段强断言：生产 profile 集合下若 `kyc.bankcard.provider.mock=true` MUST 抛错中止启动。

#### Scenario: 生产误开 Bankcard Mock 拒绝启动

- **WHEN** 当前激活 profile 为生产且 `kyc.bankcard.provider.mock=true`
- **THEN** 系统 MUST 抛出致命异常并 MUST 拒绝完成 Spring 上下文启动

#### Scenario: 厂商外呼超时

- **WHEN** Provider 调用读超时或 HTTP 5xx
- **THEN** 系统 MUST NOT 写 `status=FAILED` 终态；MUST 返回可重试语义错误码

### Requirement: 流水与审计

系统 MUST 对每次绑卡请求持久化审计流水（提交时间、`channel`=MOCK/HTTP、`provider_id`、`duration_ms`、`payload_digest`、签名校验结果），明文卡号与预留手机号 MUST NOT 入日志。审计 MUST 与 KYC 同等保护级别，仅允许内部审计接口（`@Inner` + 内网签名）查询。

#### Scenario: 审计日志脱敏

- **WHEN** 任意级别日志被采集
- **THEN** 日志中卡号 MUST 以掩码出现（如 `6225**********1234`），预留手机号 MUST 以掩码出现（如 `138****1234`）；明文 MUST NOT 出现
