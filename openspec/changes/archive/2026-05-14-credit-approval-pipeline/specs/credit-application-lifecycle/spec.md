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
