# microservice-loan-application（Delta，BREAKING）

## MODIFIED Requirements

### Requirement: 借款申请提交与状态流转

贷款申请服务 MUST 接收借款申请并生成唯一申请单号；在接收申请前 MUST 强制校验借款人同时满足以下条件：

1. **KYC 通过**：`cf_user_kyc_v2.kyc_passed=1`（即 `realname_status=VERIFIED ∧ face_status=VERIFIED`）
2. **主卡已绑定且 VERIFIED**：`cf_user_bank_card` 至少有一条 `status=VERIFIED ∧ is_primary=1`
3. **授信已通过**：`CreditApplication` 存在 `APPROVED` 且未失效的额度

任一未满足 MUST 在受理前拦截。该前置校验 MUST 通过 `microservice-user` 的内部接口（如 `/api/internal/user/eligibility`）与 `credit-application` 的内部接口完成查询，MUST NOT 跨服务直接读表。系统 MUST 维护申请状态机（至少包含：草稿/已提交/审核中/通过/拒绝/取消）；状态迁移 MUST 合法且可审计。

旧的「`step_status=3` 即视为 KYC 通过」语义 MUST 被废弃；过渡期内若 `crediflow.kyc.use-v2=true`（默认 true），MUST 仅以 `kyc_passed=1` 为准。

#### Scenario: 用户未完成新 KYC 提交借款

- **WHEN** 尚未通过 KYC v2（`kyc_passed=false`）的用户调用借款申请接口
- **THEN** 系统 MUST 拦截该请求，并返回「尚未通过 KYC 认证」错误提示；MUST NOT 调用 `fund-channel-gateway` 或写入借款单

#### Scenario: 已 KYC 未绑卡

- **WHEN** `kyc_passed=true` 但 `cf_user_bank_card` 没有 VERIFIED + is_primary=1 的卡
- **THEN** 系统 MUST 拦截该请求并返回「请先完成银行卡四要素绑卡」语义；MUST NOT 进入受理流程

#### Scenario: 非法状态迁移被拒绝

- **WHEN** 调用方尝试从终态（拒绝或取消）迁移到非允许状态
- **THEN** 系统 MUST 拒绝操作并返回可诊断错误码

### Requirement: 资料审核与资格校验

系统 MUST 支持资料审核记录（审核人、时间、结论、备注）；在放款资格校验前 MUST 校验授信有效性、主卡有效性（`is_primary=1 ∧ status=VERIFIED`）与合同签署完成情况（与合同服务协作的契约由集成层实现）。

#### Scenario: 未授信通过不可进入放款队列

- **WHEN** 申请单尝试进入放款资格校验且授信未通过
- **THEN** 系统 MUST 拒绝并 MUST 记录原因

#### Scenario: 主卡缺失不可进入放款队列

- **WHEN** 申请单尝试进入放款资格校验但用户已无 VERIFIED 主卡（解绑后未补绑）
- **THEN** 系统 MUST 拒绝并提示「请先补充银行卡四要素绑卡」；MUST NOT 进入合同生成与放款链路

## ADDED Requirements

### Requirement: 借款受理使用绑卡 token

借款申请通过、合同就绪并进入放款受理时，向 `fund-channel-gateway` 下发的请求体中 MUST 仅使用 `bindCardId`（由 `bankcard-four-elements-binding` 发放）作为收款卡引用；MUST NOT 在借款服务进程内出现卡号 / 预留手机号明文，MUST NOT 把卡号字段透传到资金网关。

#### Scenario: 受理放款仅传 bindCardId

- **WHEN** `CONTRACT_READY_EVENT` 处理器准备调用 `fund-channel-gateway` 受理放款
- **THEN** 请求 body 中收款卡字段 MUST 是 `bindCardId`；MUST NOT 出现卡号明文 / 掩码 / 厂商原始卡号 token
