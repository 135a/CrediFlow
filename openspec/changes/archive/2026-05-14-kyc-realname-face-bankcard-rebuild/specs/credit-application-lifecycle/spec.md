# credit-application-lifecycle（Delta，BREAKING）

## MODIFIED Requirements

### Requirement: 授信申请状态机流转

系统 MUST 为用户的每一笔授信额度申请创建 `CreditApplication` 记录；申请 MUST 包含 `PENDING` (机审中)、`APPROVED` (机审通过)、`REJECTED` (机审拒绝) 三种状态。

发起授信申请之前，系统 MUST 强制校验当前用户同时满足以下**两个**前置条件：

1. KYC 通过：`cf_user_kyc_v2.kyc_passed=1`（即实名 VERIFIED ∧ 实人 VERIFIED）
2. 主卡已绑定且 VERIFIED：`cf_user_bank_card` 至少有一条 `status=VERIFIED ∧ is_primary=1`

任一未满足 MUST 在受理前拒绝；MUST NOT 仅以旧 `cf_user_kyc.step_status=3` 作为放行依据。授信前置校验 MUST 通过统一 SDK / 内部接口（如 `microservice-user` 的 `/api/internal/user/eligibility`）查询，MUST NOT 由 `credit-application` 服务直接读取 `cf_user_kyc_v2` / `cf_user_bank_card` 表。

#### Scenario: 发起授信申请落库为机审中

- **WHEN** 用户调用接口发起授信额度申请，且已通过 KYC 与主卡绑定
- **THEN** 系统 MUST 在数据库插入一条状态为 `PENDING` 的申请记录，并返回申请受理成功的响应，而 MUST NOT 阻塞等待模型结果

#### Scenario: KYC 未通过尝试发起授信

- **WHEN** `kyc_passed=false` 的用户发起授信额度申请
- **THEN** 系统 MUST 在受理前拒绝并返回「请先完成 KYC 实名实人核验」语义；MUST NOT 写入 `CreditApplication` 记录

#### Scenario: 已 KYC 但未绑卡

- **WHEN** `kyc_passed=true` 但用户没有任何 `cf_user_bank_card.status=VERIFIED ∧ is_primary=1` 的卡
- **THEN** 系统 MUST 在受理前拒绝并返回「请先完成银行卡四要素绑卡」语义；MUST NOT 写入 `CreditApplication` 记录

#### Scenario: 大模型响应拒绝

- **WHEN** 异步风控机审判定该申请不予通过
- **THEN** 系统 MUST 将该申请记录更新为 `REJECTED`，并 MUST 记录大模型返回的具体原因到 `auditReason` 字段

#### Scenario: 大模型响应通过

- **WHEN** 异步风控机审判定该申请可以通过并给出建议额度
- **THEN** 系统 MUST 将该申请记录更新为 `APPROVED`，并基于大模型的建议生成最终的 `CreditResult` 可用额度

## ADDED Requirements

### Requirement: 主卡失效时的授信拦截

系统 MUST 在 `CreditApplication` 已 APPROVED 但用户解绑唯一主卡后，对下游借款受理执行兜底拦截：当 `cf_user_bank_card` 不再有任何 VERIFIED + is_primary=1 的卡时，`microservice-loan-application` 在受理阶段 MUST 拒绝，提示用户重新绑卡。授信记录本身 MUST NOT 因主卡解绑被直接撤销，但 MUST 在用户态视图中表达「需补充绑卡」。

#### Scenario: 主卡解绑后借款被拦截

- **WHEN** 用户解绑唯一主卡后尝试发起借款
- **THEN** 借款受理 MUST 拒绝并提示「请先补充银行卡四要素绑卡」；授信额度记录 MUST 保留
