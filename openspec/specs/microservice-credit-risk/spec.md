## Purpose

TBD
## Requirements
### Requirement: 授信评估与规则校验

授信风控服务 MUST 基于用户资料、征信占位数据与内部规则集输出授信建议；用户提交申请时 MUST 先行验证用户是否通过 KYC 认证（`step_status=3`），未通过时 MUST 拦截申请；验证通过后 MUST 先行落库 `PENDING` 状态记录；MUST 执行异步风控规则校验并在机审完成后更新状态流转。此外，系统 MUST 将 KYC 认证中提取的真实年龄、职业、月收入等核心属性注入到 Agent 决策的输入上下文中。

#### Scenario: 用户未完成 KYC 提交申请
- **WHEN** 尚未通过全部 KYC 认证步骤的用户调用授信申请接口
- **THEN** 系统 MUST 拦截该请求，并返回“尚未通过kyc认证”的错误提示

#### Scenario: 用户提交授信申请

- **WHEN** 用户已通过 KYC 并调用授信申请接口
- **THEN** 系统 MUST 立即返回带有单号的 `PENDING` 状态响应，并避免前端同步阻塞等待大模型

#### Scenario: 规则拒绝

- **WHEN** 用户触发硬性拒绝规则或被大模型机审拒绝
- **THEN** 系统 MUST 将申请流水状态更新为拒绝（如 `REJECTED`）且 MUST 记录规则命中代码或大模型 `auditReason` 审计信息

### Requirement: Agent 建议与最终裁决边界

系统 MUST 支持可选调用 Python Agent 获取风险分析与建议；Agent 输出 MUST 仅作为辅助输入；最终授信通过/拒绝与额度 MUST 由本服务基于确定性规则与人工策略（若启用）裁决。

#### Scenario: Agent 不可用

- **WHEN** Agent 服务超时或返回错误
- **THEN** 系统 MUST 仍可完成基于规则的授信决策或 MUST 进入人工审核队列且 MUST NOT 自动通过高风险路径

### Requirement: 风险等级与结果可追溯

系统 MUST 持久化授信申请、评估输入摘要、规则版本、模型/Agent 版本（若使用）与输出结果；结果 MUST 可被按申请单号查询用于审计。

#### Scenario: 审计查询

- **WHEN** 审计方查询某授信申请单的评估轨迹
- **THEN** 系统 MUST 返回时间序列事件与关键决策字段（不含明文敏感信息）



# microservice-credit-risk（Delta，BREAKING）

## MODIFIED Requirements

### Requirement: 授信评估与规则校验

授信风控服务 MUST 基于用户资料、内部事实数据与版本化规则集输出授信结论。用户提交申请时 MUST 先通过用户服务 `eligibility`（`kyc_passed=true` 且存在 `VERIFIED` 主卡）校验；未通过时 MUST 拦截且 MUST NOT 落 `CreditApplication`。通过后 MUST 依次执行：

1. **硬规则前置**：身份证 / 手机号 / 设备黑名单；未结清逾期；账号状态异常 — 任一命中 MUST 直接 `REJECTED` 且 MUST NOT 进入评分。
2. **四维评分**：计算 `S1..S4` 与 `TotalScore`、划分 `model_risk_level`（`LOW|MEDIUM|HIGH`），落 `cf_credit_score`。`model_risk_level` 由 Java 评分引擎**独占**输出，Agent MUST NOT 修改或替代。
3. **行为意图独立评估（可选）**：可选调用 Agent `credit_behavior_intent` 输出 `behavior_intent_status ∈ {BEHAVIOR_NORMAL, BEHAVIOR_SUSPICIOUS}`；Agent 不可用 / 关闭 / 超时 → 兜底 `BEHAVIOR_NORMAL`。
4. **矩阵分流（确定性）**：按 `credit-agent-governance` 定义的 `model_risk_level × behavior_intent_status` 矩阵决定是否二次人脸与终态路径；MUST NOT 引入矩阵外路径。
5. **对话意图升级**：异步消费 `ChatRiskEscalationSignal`，仅可触发 `FORCE_SECONDARY_FACE` / `QUOTA_CAP` / `MANUAL_REVIEW` 三种已定义动作；MUST NOT 用于上调 / 下调 `model_risk_level`。
6. **异步机审收尾**：额度写入、合同触发、状态迁移；MUST NOT 让前端同步阻塞等待 Agent。

系统 MUST 将 KYC v2 与设备 / IP / 行为摘要等**脱敏**特征注入评分与 Agent 上下文；MUST NOT 在日志或 MQ 明文输出完整证件号 / 卡号 / 用户原话。

#### Scenario: 未完成 KYC 或主卡

- **WHEN** `kyc_passed=false` 或无 `VERIFIED` 主卡
- **THEN** 系统 MUST 拒绝受理且 MUST NOT 创建申请单

#### Scenario: 用户提交授信申请

- **WHEN** 用户已通过 KYC 与主卡并调用授信申请接口
- **THEN** 系统 MUST 立即返回带单号的中间态响应（如 `PENDING_HARD_RULES` 或统一 `PENDING` 映射），且 MUST NOT 阻塞等待 Agent

#### Scenario: 硬规则拒绝

- **WHEN** 用户命中硬规则
- **THEN** 系统 MUST 置 `REJECTED` 且 MUST 记录 `hard_rule_codes[]`

#### Scenario: 机审拒绝（评分或路径拒绝）

- **WHEN** 矩阵分流路径判定拒绝或二次人脸失败
- **THEN** 系统 MUST 置 `REJECTED` 且 SHOULD 持久化 `risk_insight`（若 Agent 生成失败则写占位）

### Requirement: Agent 输出消费与边界

系统 MUST 仅消费 Agent 的两类信号：（1）`behavior_intent_status`（二元）与 evidence；（2）拒绝洞察文案。MUST 一律忽略并审计任何旧式或越权字段（`agent_risk_proposal` / `routing_escalation` / `SKIP_FACE` / `REQUIRE_FACE` 等等价于改动等级或单独控制人脸开关的字段）。Agent MUST NOT 直接调用放款 / 代扣 / 写额度 API；终局授信状态、额度写入、合同触发、人工队列入队 MUST 仅由本服务执行。

#### Scenario: Agent 不可用

- **WHEN** Agent 超时或返回错误
- **THEN** 系统 MUST 把 `behavior_intent_status` 兜底为 `BEHAVIOR_NORMAL`；MUST 按矩阵分流；MUST NOT 自动通过 `HIGH` 风险路径

#### Scenario: Agent 越权字段被忽略

- **WHEN** Agent 输出包含 `routing_escalation` 或 `SKIP_FACE` 等字段
- **THEN** 系统 MUST 丢弃这些字段并 MUST 写一次审计 `AGENT_GOVERNANCE_VIOLATION`

#### Scenario: 行为可疑只触发二次人脸

- **WHEN** `model_risk_level=LOW` 且 `behavior_intent_status=BEHAVIOR_SUSPICIOUS`
- **THEN** 系统 MUST 仅追加二次人脸；过 → `APPROVED`；不过 → `REJECTED`；MUST NOT 把申请按 `MEDIUM` 路径再行评估

## ADDED Requirements

### Requirement: 内部对话升级接入

系统 MUST 暴露 `POST /api/internal/credit/risk/chat-escalation`（内网签名），接收 `ChatRiskEscalationSignal`；MUST 幂等；MUST 将升级动作写入审计与（可选）`cf_credit_risk_escalation` 表。升级动作仅可来自既定枚举集合（`FORCE_SECONDARY_FACE` / `QUOTA_CAP` / `MANUAL_REVIEW`），MUST NOT 包含「修改等级」类语义。

#### Scenario: 重复升级

- **WHEN** 同一用户在 1 小时内重复发送等价升级信号
- **THEN** 系统 MUST 合并为一条审计且 MUST NOT 重复冻结额度多次

### Requirement: 授信服务内部 API 强类型契约

`credit-risk-service` 对外（含 `/api/internal/credit/*` 与 `/api/app/credit/*`）暴露的业务响应体，在 Java 服务层 MUST 使用具名 DTO/View 类型承载，MUST NOT 以无约束的 `Map<String, Object>` 作为 Service 接口的返回类型或核心业务方法的参数类型（框架层 `Result` 包装除外）。

#### Scenario: 内部申请接口返回明确结构

- **WHEN** 调用方请求 `POST /api/internal/credit/apply` 或等价内部申请能力
- **THEN** 响应 `data` MUST 可反序列化为包含 `applicationId` 与业务状态字段的明确类型，且字段语义与改造前 JSON 契约兼容。

#### Scenario: 查询最近申请状态

- **WHEN** 调用方请求用户最近授信申请状态
- **THEN** 服务 MUST 返回强类型视图对象；当用户无申请记录时 MUST 使用文档化的查询哨兵值（如 `NOT_APPLIED`），且该哨兵 MUST NOT 作为数据库 `status` 列的合法持久化值。

### Requirement: Agent 降级文案可配置与可维护

当 `agent-service` 不可用触发 Feign Fallback 时，返回给用户或管理员的固定中文说明 MUST 集中定义（常量类或配置文件），MUST NOT 散落在 Fallback 方法体内以难以检索的字符串字面量维护；降级用数值阈值 MUST 继续支持外部配置。

#### Scenario: 降级文案单点维护

- **WHEN** 运维或产品需要调整降级提示文案
- **THEN** MUST 能在不超过两处集中定义的位置完成修改（如 `AgentFallbackMessages` 或 `agent-fallback.properties`），而无需在多个 Fallback 方法中重复搜索替换。

