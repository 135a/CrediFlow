# credit-application-lifecycle Specification

## Purpose

定义用户授信额度申请的异步生命周期、状态机与审计字段；明确机审通过/拒绝后的落库与额度生成规则。授信受理前 MUST 与 KYC v2（`kyc_passed`）及主卡四要素绑卡（`VERIFIED` 主卡）对齐，并通过用户服务内部 eligibility 接口查询，避免跨服务直读用户表。

## Requirements

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

### Requirement: 主卡失效时的授信拦截

系统 MUST 在 `CreditApplication` 已 APPROVED 但用户解绑唯一主卡后，对下游借款受理执行兜底拦截：当 `cf_user_bank_card` 不再有任何 VERIFIED + is_primary=1 的卡时，`microservice-loan-application` 在受理阶段 MUST 拒绝，提示用户重新绑卡。授信记录本身 MUST NOT 因主卡解绑被直接撤销，但 MUST 在用户态视图中表达「需补充绑卡」。

#### Scenario: 主卡解绑后借款被拦截

- **WHEN** 用户解绑唯一主卡后尝试发起借款
- **THEN** 借款受理 MUST 拒绝并提示「请先补充银行卡四要素绑卡」；授信额度记录 MUST 保留


# credit-application-lifecycle（Delta，BREAKING）

## MODIFIED Requirements

### Requirement: 授信申请状态机流转

系统 MUST 为用户的每一笔授信额度申请创建 `CreditApplication` 记录。状态集合 MUST 扩展以覆盖：硬规则校验中、评分中、矩阵分流 / 二次人脸中、人工审核中、机审通过待签合同、授信生效完成、拒绝、过期等（具体枚举名实现阶段锁定，但语义 MUST 可映射到以下阶段）：`PENDING_HARD_RULES → PENDING_SCORING → PENDING_ROUTING → PENDING_SECONDARY_FACE → PENDING_MANUAL_REVIEW → APPROVED → CONTRACT_PENDING → COMPLETED | REJECTED | EXPIRED`。

路由所依据的风险等级 MUST 为 `model_risk_level`（由 Java 评分引擎独占输出，**不允许**任何 Agent 字段修改）；分流策略 MUST 严格采用 `credit-agent-governance` 中定义的基于 `model_risk_level` 的确定性矩阵；MUST NOT 引入矩阵外路径。

发起授信申请之前，系统 MUST 强制校验：`kyc_passed=1` 且存在 `VERIFIED` 主卡（经用户服务内部接口）。任一不满足 MUST 拒绝且 MUST NOT 写申请单。

额度与合同：机审达到可开通条件后，系统 MUST 计算初始循环额度并写 `cf_user_credit_quota`；MUST 触发 `CREDIT_CONTRACT` 签署；用户未在配置时限内签署 MUST 转 `EXPIRED`。

#### Scenario: 发起授信申请

- **WHEN** 用户调用开通授信且前置满足
- **THEN** 系统 MUST 创建申请单并返回单号；MUST NOT 同步阻塞等待 Agent

#### Scenario: 低风险正常 → 直接通过

- **WHEN** `model_risk_level=LOW`
- **THEN** 申请 MUST 自动 `APPROVED`，且 MUST NOT 进入 `PENDING_SECONDARY_FACE`

#### Scenario: 中风险必二次人脸

- **WHEN** `model_risk_level=MEDIUM`
- **THEN** 申请 MUST 进入 `PENDING_SECONDARY_FACE`；过 → `APPROVED`；不过 → `REJECTED`

#### Scenario: 高风险二次人脸后人工

- **WHEN** `model_risk_level=HIGH` 且二次人脸通过
- **THEN** 申请 MUST 进入 `PENDING_MANUAL_REVIEW`；MUST NOT 自动 `APPROVED`

#### Scenario: 合同未签过期

- **WHEN** `CONTRACT_PENDING` 超过 `crediflow.credit.contract.sign-timeout-hours`
- **THEN** 系统 MUST 将申请置为 `EXPIRED` 且 MUST NOT 保留可用额度

## ADDED Requirements

### Requirement: 用户安全洞察查询

系统 MUST 提供（经 BFF）`GET /api/app/credit/last-result`，返回最近一笔授信结果；若拒绝 MUST 包含 `userSafeInsight`（来自持久化 `risk_insight` 的安全子集）。

#### Scenario: 无申请记录

- **WHEN** 用户从未申请
- **THEN** 系统 MUST 返回空结果而非 500

