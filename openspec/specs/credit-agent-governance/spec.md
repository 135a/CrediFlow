# credit-agent-governance

## Purpose

定义 Agent 在授信域内的**权责边界**与**输出语义**：Java 模型独立给出 `model_risk_level`（信用基本面），Agent 独立给出 `behavior_intent_status`（实时行为 + 意图），由 Java 服务以**确定性矩阵**做分流。Agent **不修改、不升级、不降级** `model_risk_level`，也不做任何终局动作。

## ADDED Requirements

### Requirement: Agent 输出必须为结构化信号

Agent MUST 仅返回 JSON Schema 校验通过的结构体（含 `signalType`、`confidence`、`evidenceTags[]`、`evidenceSummary`）；MUST NOT 返回可执行 SQL；MUST NOT 包含完整身份证号 / 银行卡号 / 用户原话明文（仅可携带脱敏摘要与标签）。

#### Scenario: Schema 校验失败

- **WHEN** Agent 输出无法通过 schema 校验
- **THEN** 消费方 MUST 视为 `BEHAVIOR_NORMAL` 兜底并 MUST 记安全告警

### Requirement: 权责分离 —— Agent 不得修改信用等级

`model_risk_level`（`LOW | MEDIUM | HIGH`）由 Java 评分引擎独占输出，**MUST** 直接作为路由依据。Agent **MUST NOT** 输出任何等价于「上调」「下调」或「替代」`model_risk_level` 的信号；Java 服务 **MUST NOT** 接受此类信号；若历史协议字段存在（如 `agent_risk_proposal` / `routing_escalation` / `SKIP_FACE`），Java MUST 一律忽略并写一次审计 `AGENT_GOVERNANCE_VIOLATION`。

#### Scenario: Agent 误传等级建议

- **WHEN** Agent 输出含 `routing_escalation=MEDIUM` 或 `agent_risk_proposal` 字段
- **THEN** Java MUST 丢弃该字段并 MUST 以 `model_risk_level` 原样执行；MUST 记审计

#### Scenario: Agent 误传二次人脸跳过

- **WHEN** Agent 输出 `faceGateDecision=SKIP_FACE` 或等价信号
- **THEN** Java MUST 一律忽略；二次人脸是否触发 MUST 由分流矩阵决定

### Requirement: 对话意图风控升级 (Chat Risk Escalation)

当用户在 APP 与 AI 进行客服或交互对话时，Agent MUST 实时评估用户的“对话意图”。如果发现如套现诱导、逃废债、明确表示不想还款、试探借完卸载等明显非正常意图，Agent MUST 触发风控升级，输出 `ChatRiskEscalationSignal`。该信号将被投递至后台管理系统并落库。

信号内容 MUST 包含：
1. **`intentTags[]`**：意图标签（如 `CASH_OUT_INTENT`, `NO_REPAYMENT_INTENT` 等）
2. **`relevantChatLogs[]`**：**关键！** 必须包含触发风险的上下文聊天记录原话，供后台审核员追溯。
3. **`agentSuggestions`**：Agent 给出的处置建议（如：建议直接冻结额度、建议人工介入核实还款意愿等）。

后台收到该信号后，MUST 仅将用户/申请单加入 `cf_credit_review_queue`，并在审核台展示这些聊天记录和建议。Agent MUST NOT 输出任何直接的机器干预动作（如修改额度或强制人脸）。

#### Scenario: 聊天中暴露不想还款意图
- **WHEN** 用户在对话中表示“借了如果不想还怎么办”并被意图模型高置信命中
- **THEN** Agent MUST 触发 `ChatRiskEscalationSignal`，携带用户的相关聊天记录与风控建议，发送至后台管理系统的审核队列中。

#### Scenario: 正常用户聊天
- **WHEN** 老用户正常咨询费率
- **THEN** Agent MUST 不触发任何升级信号。

### Requirement: Java 确定性分流（硬性路由）

Java 服务 MUST 按 `model_risk_level` 进行确定性分流，**MUST** 与本规格定义保持一致，Agent 无法干预此路由逻辑：

| `model_risk_level` | 二次人脸 | 终态路径 |
| --- | --- | --- |
| `LOW` (低风险) | 免人脸 | 直接自动审批通过 → `APPROVED`（免人工） |
| `MEDIUM` (中风险) | **强制** | 人脸通过 → `APPROVED`；人脸失败 → `REJECTED` |
| `HIGH` (高风险) | **强制** | 人脸通过 → `PENDING_MANUAL_REVIEW`（转人工）；人脸失败 → `REJECTED` |

`decision_source` MUST 记录命中格，例如 `MATRIX_LOW_AUTO_PASS` 或 `MATRIX_HIGH_MANUAL_REVIEW`。**MUST NOT** 引入超出该矩阵的其它分流路径。

#### Scenario: 低风险正常 → 直过
- **WHEN** `model_risk_level=LOW`
- **THEN** 系统 MUST 自动通过且 MUST NOT 触发二次人脸

#### Scenario: 中风险 → 必二次人脸
- **WHEN** `model_risk_level=MEDIUM`
- **THEN** 系统 MUST 触发二次人脸；通过则 `APPROVED`，失败则 `REJECTED`

#### Scenario: 高风险必二次人脸 + 人工
- **WHEN** `model_risk_level=HIGH` 且二次人脸通过
- **THEN** 系统 MUST 转 `PENDING_MANUAL_REVIEW`，MUST NOT 自动 `APPROVED`

### Requirement: 人工审核辅助（Agent 自动化三件套）

当授信申请进入 `PENDING_MANUAL_REVIEW` 后，系统 SHOULD 调用 Agent 的 `manual_review_assistant` 能力。Agent 自动输出「三件套」给审核员，辅助人工决策。输出结果结构化落库，供管理端审核台展示：

1. **具体风险明细**：明确列出哪几项指标异常（例如：命中多头借贷、近期逾期、常用设备变更等），作为扣分或风险的直接解释。
2. **量化概率**：输出 `defaultProbability`（量化违约概率，如 12.5%）与 `fraudProbability`（欺诈概率，如 3.1%）。
3. **审核建议**：`auditRecommendation` MUST 仅限以下四种枚举之一：
   - `RECOMMEND_PASS` (建议放行)
   - `RECOMMEND_DOWNGRADE` (建议降额)
   - `RECOMMEND_REJECT` (建议拒绝)
   - `RECOMMEND_LIMIT_TERMS` (建议限制期数)

#### Scenario: Agent 输出人工审核三件套
- **WHEN** 用户处于高风险人工审核队列
- **THEN** 审核台 MUST 展示 Agent 生成的风险明细、违约/欺诈概率以及四大审核建议之一。

### Requirement: Agent 不可用 / 降级 MUST 不阻断主链路

当 `crediflow.credit.agent-behavior-intent=false` 或 Agent 调用超时 / 5xx / schema 校验失败时，Java MUST 把 `behavior_intent_status` 兜底为 `BEHAVIOR_NORMAL`，按矩阵继续执行；MUST 记一次降级指标。`HIGH` 风险路径不受影响（仍二次人脸 + 人工）。

#### Scenario: Agent 超时

- **WHEN** 行为意图 Agent 读超时
- **THEN** Java MUST 使用 `BEHAVIOR_NORMAL` 兜底；MUST 写 `decision_source` 标记 `AGENT_FALLBACK`

### Requirement: 拒绝洞察与可执行建议

当授信申请终态为 `REJECTED`（机审）时，系统 SHOULD 调用 Agent `credit_rejection_insight` 生成 `risk_insight`：包含主要扣分维度自然语言解释（基于 S1~S4 子规则命中码与 `behavior_intent_status`）与可执行建议列表（如「固定常用手机、本地稳定登录 30 天可提升评分与额度」）。对外 API MAY 仅返回统一短文案，但 MUST 持久化 `risk_insight` 供用户安全查询接口与管理端审计。`risk_insight` MUST NOT 暴露内部规则码或他用户数据。

#### Scenario: 用户查询上次拒绝原因

- **WHEN** 用户调用 `GET /api/app/credit/last-result` 且上一笔为拒绝
- **THEN** 响应 MUST 包含 `userSafeInsight`（脱敏子集）；MUST NOT 泄露他用户数据或内部短码
