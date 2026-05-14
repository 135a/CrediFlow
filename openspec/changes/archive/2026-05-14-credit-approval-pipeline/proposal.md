# 授信 9 步全链路 + Agent 治理（柔性二次人脸 / 对话意图风控 / 拒绝归因）

## Why

KYC 与四要素绑卡完成后，**开通授信**需要可审计、可定责的端到端流程：硬规则前置、四维加权评分、风险等级路由、循环额度、授信电子合同与 APP 展示。

同时，业务要求 **Agent 增强风控体验**，但 **不得成为主链路的单点**：无 Agent 时 Java 规则仍可完整跑通。

本次补齐四类核心能力（其中路由由 Java 死板控制，Agent 作为辅助输出）：

1. **确定性信用等级路由**：Java 评分模型独占给出信用基本面（低/中/高）。**低风险**：直接自动审批通过（免人脸、免人工）；**中风险**：强制二次人脸核验（通过则审批通过，失败则直接拒绝）；**高风险**：强制二次人脸核验（通过则转人工审核台，失败则直接拒绝）。
2. **人工审核辅助（Agent 三件套）**：高风险转人工后，Agent 自动输出给审核员三件套数据：① 该用户具体风险明细（哪几项指标异常）；② 量化违约概率、欺诈概率；③ 给出审核建议（建议放行 / 建议降额 / 建议拒绝 / 建议限制期数）。
3. **对话意图风控**：用户在 APP 与 AI 聊天时，识别套现话术、逃废债、不想还款等明显非正常意图；若发现风险，Agent 会将该风险发送给后台管理系统（转人工审核或风控升级），**并附带给出用户的相关聊天记录以及审核/操作建议**。
4. **拒绝归因与优化方案**：Java 对外仍可只返回统一短码；同时 MUST 持久化并可选返回 Agent 生成的**维度扣分解释**与**可执行提升建议**。

## What Changes

### New capabilities

- `credit-risk-scoring`：S1~S4 四维（0~100）、`TotalScore = 0.2·S1 + 0.4·S2 + 0.2·S3 + 0.2·S4`、低 / 中 / 高划分与版本化落库。
- `credit-quota-revolving`：`cf_user_credit_quota` 循环额度账户（总额 / 已用 / 可用 / 冻结）与线性额度公式。
- `credit-agent-governance`：Agent 输出的**非终局**治理信号：二次人脸闸门、对话意图升级、拒绝洞察；Java MUST 校验边界与安全底线后执行。

### Modified capabilities

- `credit-application-lifecycle` **BREAKING**：扩展授信申请状态机（硬规则 → 评分 → 路由 / 二次人脸 → 人工 → 合同 → 完成 / 拒绝 / 过期）。
- `microservice-credit-risk` **BREAKING**：实现评分与路由；集成 Agent 客户端（可选）；接收对话意图升级事件；写入 `cf_credit_score` / `cf_credit_review_queue` / `cf_credit_risk_escalation`（或等价表）。
- `agent-python`：新增授信治理相关工具与 API（结构化 JSON）；MUST NOT 直接写授信终态、MUST NOT 调放款 / 代扣。
- `kyc-face-liveness`：`bizScene=CREDIT_SECONDARY_FACE` 复用 Provider 与回调，**不**改写 KYC 主状态。
- `microservice-loan-contract`：区分 `CREDIT_CONTRACT` 与 `LOAN_CONTRACT`。
- `bff-app`：`/api/app/credit/*` 与聊天链路的升级信号投递（见 design）。
- `rag-customer-service`（或 APP 侧统一对话入口）：对话完成后 MUST 将意图分析结果投递给风控（异步、可重试）。
- `credit-admin-ops`：人工审核队列与 Agent 升级审计查询。
- `microservice-loan-application`：借款受理增加「授信电子合同 ACTIVE」类前置（与既有 KYC / 主卡 / 授信校验叠加）。
- `integration-rocketmq`：`TOPIC_CREDIT_*` 与 `TOPIC_CREDIT_CHAT_RISK_ESCALATION`（命名以 design 为准）。

## Impact

- **代码（计划）**：`credit-risk-service`、`app-bff-service`、`user-service`（二次人脸场景）、`loan-contract-service`、`loan-application-service`、`python/agent`、`crediflow-common`（事件 DTO）。
- **配置**：`crediflow.credit.scoring-v2`、`crediflow.credit.agent-face-gating`、`crediflow.credit.agent-chat-risk`、`crediflow.credit.threshold.*`、`crediflow.credit.quota.*`。
- **回滚**：上述开关置 `false` 即回退到无 Agent 的 Java 死板路径；对话升级关闭则仅记录日志不执行升级。

## Non-goals（本 change 不包含）

- 真实外部征信数据源接入（仍以占位 / 内部表为准）。
- 借款合同与授信合同的法务审版全文（仅约束字段与流程）。
