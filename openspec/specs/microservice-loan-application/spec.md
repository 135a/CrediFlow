# microservice-loan-application Specification

## Purpose

定义借款申请微服务的受理契约、申请单状态机、幂等与异步领域事件分发；约束放款受理前的前置校验与资金网关调用边界。借款受理 MUST 以 KYC v2 通过、主卡已绑定且授信有效为前提，并通过用户服务 eligibility 等内部接口聚合查询，禁止在借款服务内持有或透传卡号明文。

## Requirements

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

### Requirement: 资料审核与资格校验

系统 MUST 支持资料审核记录（审核人、时间、结论、备注）；在放款资格校验前 MUST 校验授信有效性、主卡有效性（`is_primary=1 ∧ status=VERIFIED`）与合同签署完成情况（与合同服务协作的契约由集成层实现）。

#### Scenario: 未授信通过不可进入放款队列

- **WHEN** 申请单尝试进入放款资格校验且授信未通过
- **THEN** 系统 MUST 拒绝并 MUST 记录原因

#### Scenario: 主卡缺失不可进入放款队列

- **WHEN** 申请单尝试进入放款资格校验但用户已无 VERIFIED 主卡（解绑后未补绑）
- **THEN** 系统 MUST 拒绝并提示「请先补充银行卡四要素绑卡」；MUST NOT 进入合同生成与放款链路

### Requirement: 借款受理使用绑卡 token

借款申请通过、合同就绪并进入放款受理时，向 `fund-channel-gateway` 下发的请求体中 MUST 仅使用 `bindCardId`（由 `bankcard-four-elements-binding` 发放）作为收款卡引用；MUST NOT 在借款服务进程内出现卡号 / 预留手机号明文，MUST NOT 把卡号字段透传到资金网关。

#### Scenario: 受理放款仅传 bindCardId

- **WHEN** `CONTRACT_READY_EVENT` 处理器准备调用 `fund-channel-gateway` 受理放款
- **THEN** 请求 body 中收款卡字段 MUST 是 `bindCardId`；MUST NOT 出现卡号明文 / 掩码 / 厂商原始卡号 token

### Requirement: 幂等与重复提交防护

同一幂等键下的重复提交 MUST 被拦截并防止多次执行业务逻辑。在借款申请接口上，必须强制校验从前端传入的 `idmpToken`。服务端 MUST 使用基于 Redis 的分布式锁以该 Token 为键加锁，以保证绝对的请求互斥。

#### Scenario: 客户端重试重复提交

- **WHEN** 客户端由于连点或网络超时，使用相同 `idmpToken` 连续多次调用借款申请接口
- **THEN** 系统 MUST 获取到 Redis 分布式锁来处理第一次请求，而对无法获取锁的后续并发请求，系统 MUST 抛出“请勿重复提交申请”的业务异常，且 MUST NOT 创建第二张申请单

### Requirement: 借款通过后的异步事件分发

借款申请被终审通过后，系统 MUST 向外部投递借款通过的异步领域事件，以解耦后续的合同生成与放款流程；后续实际触达资金方的放款 HTTP 调用 MUST 仅在风控与业务审核通过、合同就绪后，由 Go 资金网关统一执行，Java 借款申请服务 MUST NOT 直连资金方。

#### Scenario: 借款申请成功通过

- **WHEN** 风控与业务审核双重通过一笔借款申请
- **THEN** 系统 MUST 将借款单状态置为「处理中（生成合同阶段）」，并 MUST 向 MQ 发送 `LOAN_APPROVED_EVENT` 事件；后续放款执行链 MUST 在 `CONTRACT_READY_EVENT` 之后由资金流水/集成层调用 Go 资金网关受理放款，且 MUST NOT 在 Java 进程内发起对资金方的带签外呼
