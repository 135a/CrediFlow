# agent-python（Delta）

## ADDED Requirements

### Requirement: 授信治理工具链（非终局）

Python Agent MUST 提供授信域可调用的工具（HTTP 或 MCP，具体实现由工程选定），范围 **严格限定** 在以下两个工具（与一个可选拒绝洞察工具），输出 MUST 为结构化 JSON：

1. `credit_behavior_intent` — 综合「设备操作行为 + 环境与登录画像 + 对话意图」三类信号，输出 `behavior_intent_status ∈ {BEHAVIOR_NORMAL, BEHAVIOR_SUSPICIOUS}` 与 `evidenceTags[] / evidenceSummary / confidence`。MUST NOT 输出风险等级、二次人脸开关、是否人工等终局类字段。
2. `credit_rejection_insight` — 输入子规则命中摘要与 `behavior_intent_status`，输出用户安全文案 + 可执行提升建议。
3.（历史兼容）若旧调用方仍传 `credit_routing_escalate` / `credit_face_gate` / `credit_chat_intent_risk`，工具实现 MUST 仅返回等价的 `credit_behavior_intent` 结构；Java 侧 MUST 一律按新规格语义消费，旧字段（`agent_risk_proposal` / `routing_escalation` / `SKIP_FACE`）MUST 被丢弃。

工具 MUST 只做推理与结构化输出；MUST NOT 包含写授信状态的副作用；密钥与模型配置 MUST 来自环境变量或密钥管理。

#### Scenario: Agent 误传等级字段

- **WHEN** 工具实现产出 `agent_risk_proposal` 或 `routing_escalation`
- **THEN** Java 消费方 MUST 丢弃且 MUST 写一次审计

#### Scenario: 正常咨询

- **WHEN** 用户在对话中仅咨询费率或还款日
- **THEN** `credit_behavior_intent` MUST 输出 `BEHAVIOR_NORMAL`

### Requirement: 行为意图判定的三类输入

`credit_behavior_intent` 的输入 MUST 显式覆盖三类信号（任一类缺失视为该类信号为「不可疑」）：

- 设备：是否新设备、近 N 分钟授信页进出次数、申请重发起次数、点击节奏指标、同设备多账号标记。
- 环境：跨省异地、常用城市突变、当前小时是否高危时段、模拟器 / 虚拟机 / ROOT / 伪装定位、代理 / VPN 标记。
- 对话：脱敏后的对话标签序列（如 `CASH_OUT_INTENT / DEBT_DODGING / RULE_PROBING / EVASION`），MUST NOT 直接送入用户原话明文。

#### Scenario: 缺失对话上下文

- **WHEN** 仅有设备与环境信号，无对话上下文
- **THEN** Agent MUST 基于已知信号判定且 MUST 在 `evidenceTags` 标注 `NO_CHAT_CONTEXT`

## MODIFIED Requirements

### Requirement: 不参与核心事务与终局裁决

Agent MUST NOT 直接执行放款、扣款、授信通过等终局动作；此类动作 MUST 由 Java 服务基于规则与权限完成。**补充**：Agent 在授信域的输出严格限定为 `behavior_intent_status` 与拒绝洞察草稿；MUST NOT 输出风险等级建议、MUST NOT 直接决定二次人脸是否触发；终局裁决与矩阵分流均由 Java 服务执行。

#### Scenario: 工具链尝试写库

- **WHEN** 编排层检测到工具调用目标为未白名单的写 SQL 或写 HTTP
- **THEN** 系统 MUST 拒绝并 MUST 记录拦截原因
