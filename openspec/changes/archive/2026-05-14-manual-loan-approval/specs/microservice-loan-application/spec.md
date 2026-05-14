## MODIFIED Requirements

### Requirement: 借款申请提交与状态流转

贷款申请服务 MUST 接收借款申请并生成唯一申请单号；在接收申请前 MUST 强制校验借款人同时满足以下条件：

1. **KYC 通过**：`cf_user_kyc_v2.kyc_passed=1`（即 `realname_status=VERIFIED ∧ face_status=VERIFIED`）
2. **主卡已绑定且 VERIFIED**：`cf_user_bank_card` 至少有一条 `status=VERIFIED ∧ is_primary=1`
3. **授信已通过**：存在有效授信（与 `credit-risk-service` 协作的内部查询契约）
4. **借款期数合法**：入参中的 `term` MUST 是被允许的枚举值集合（3、6、12）之一

任一未满足 MUST 在受理前拦截。该前置校验 MUST 通过 `microservice-user` 的内部接口（如 `/api/internal/user/eligibility`）与授信服务的内部接口完成查询，MUST NOT 跨服务直接读表。系统 MUST 维护申请状态机（至少包含：草稿/已提交/审核中/待人工审核/通过/拒绝/取消）；状态迁移 MUST 合法且可审计。特别是当风控引擎返回需人工复核时，状态 MUST 从审核中流转到待人工审核 (`PENDING_MANUAL_REVIEW`)。

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

#### Scenario: 用户传入非法期数被拒绝

- **WHEN** 用户调用借款接口，传入 `term=4`
- **THEN** 系统 MUST 拒绝受理，并返回明确的期数不合法提示

#### Scenario: 触发人工审核流转

- **WHEN** 风控服务判定当前贷款申请处于疑似风险区间并返回 `MANUAL_REVIEW`
- **THEN** 贷款申请服务 MUST 将该申请单状态从“审核中”更新为“待人工审核 (`PENDING_MANUAL_REVIEW`)”，并阻断自动放款流程
