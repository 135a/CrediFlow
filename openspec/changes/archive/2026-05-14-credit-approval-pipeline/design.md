# Design：授信 9 步 + Agent 治理

## 1. 九步主链路（Java 为主）

1. APP → `app-bff-service`：用户点击「开通授信额度」。
2. BFF → `credit-risk-service`：`POST /api/app/credit/apply`（内部转发）创建 `CreditApplication`。
3. **硬规则**：身份证 / 手机号 / 设备黑名单；未结清逾期；账号状态。命中 → `REJECTED`，结束。
4. **评分**：计算 S1~S4 与 `TotalScore`，得到 **模型信用等级** `model_risk_level ∈ {LOW | MEDIUM | HIGH}`，落 `cf_credit_score`。该等级由 Java 评分引擎**独占输出**，下游 Agent **不得**修改、上调、下调或替代。
5. **确定性路由与人工审核辅助（Agent 赋能）**：
   - **Java 确定性分流**：
     | `model_risk_level` | 二次人脸 | 终态路径 |
     | --- | --- | --- |
     | LOW (低风险) | 免人脸 | **自动通过 → `APPROVED`**（免人工） |
     | MEDIUM (中风险) | **强制二次人脸** | 人脸通过 → `APPROVED`；人脸失败 → `REJECTED` |
     | HIGH (高风险) | **强制二次人脸** | 人脸通过 → `PENDING_MANUAL_REVIEW`（转人工）；人脸失败 → `REJECTED` |

   - **转人工后 AI Agent 能力**（异步执行）：进入 `PENDING_MANUAL_REVIEW` 后，Agent 服务自动调用大模型生成**审核三件套**辅助人工审核。审核员在后台管理系统加载数据时可见：
     1. **该用户具体风险明细**（明确列出哪几项指标异常，例如多头借贷、近期逾期等）
     2. **量化违约概率、欺诈概率**（例如违约率 12.5%，欺诈率 3.1%）
     3. **给出审核建议**（必须为以下四种之一：建议放行 / 建议降额 / 建议拒绝 / 建议限制期数）
6. **额度**：`UserQuota = MinQuota + (clamp(TotalScore,60,100) − 60) / 40 × (MaxQuota − MinQuota)`；`HIGH` 且未进入人工批准路径前不算额度。
7. **额度账户**：写 `cf_user_credit_quota`（`total / used / available / frozen`），循环模型。
8. **授信电子合同**：`loan-contract-service` 生成 `CREDIT_CONTRACT`，用户签署后 `ACTIVE`。
9. **APP 展示**：总额度、可用、循环说明（BFF 聚合只读接口）。

## 2. 对话意图风控（Agent → Java）

- **触发点**：用户在 APP 与 AI 对话（经 `rag-customer-service` 或统一对话 BFF，以实际代码为准）。
- **Agent 输出**：`ChatRiskEscalationSignal`：`intentTags[]`（如逃废债、不想还款）、`severity`、`evidenceSummary`、**`relevantChatLogs[]`**（包含发现风险的相关原话聊天记录，供后台审核员查看）、**`agentSuggestions`**（对审核员的具体建议，如建议人工核实还款意愿）。
- **投递**：MQ 或内部同步 API → `credit-risk-service` 的 `POST /api/internal/credit/risk/chat-escalation`，将风险上报至后台管理系统。
- **Java 执行**：
  - `MANUAL_REVIEW`（唯一兜底动作）：Java 收到该信号后，**仅负责**将该申请或用户入队 `cf_credit_review_queue`，来源标记 `CHAT_INTENT`。审核员在后台管理系统即可看到该用户的相关聊天记录及 Agent 建议。
- **底线**：Agent **不得直接参与或干预风控主链路**（如不得触发降额、不得触发强制人脸、不得改写硬规则或信用等级）。AI 发明的风险只负责流转至后台管理员进行人工审核。

## 3. 拒绝归因与优化建议

- **Java 对外**：HTTP 仍可返回统一 `message`（合规短文案）。
- **持久化**：`cf_credit_application` 扩展 JSON 列 `risk_insight`（或旁表 `cf_credit_rejection_insight`）：`top_penalty_dimensions[]`、`natural_language_summary`、`actionable_tips[]`、`agent_model_version`、`schema_version`。
- **Agent 输入**：脱敏后的 `S1..S4`、各子规则命中码、硬规则结果（无证件全号）。
- **用户可见**：APP 查询 `GET /api/app/credit/last-result` 返回 `userSafeInsight`（过滤内部码）。

## 4. 数据表（草案）

- `cf_credit_score`：`application_id`、`s1..s4`、`total_score`、`risk_level`、`rules_version`、`created_at`。
- `cf_user_credit_quota`：`user_id`、`total_amount`、`used_amount`、`available_amount`、`frozen_amount`、`version`。
- `cf_credit_review_queue`：`application_id`、`source`（`HIGH_RISK_FACE` / `CHAT_INTENT` / …）、`status`。
- `cf_credit_risk_escalation`（或事件表）：`user_id`、`source`、`payload_digest`、`actions_applied[]`、`created_at`。

## 5. 合同与借款耦合

- 授信 `APPROVED` 后进入 `CONTRACT_PENDING`；签署成功 → `COMPLETED`（或 `APPROVED_ACTIVE` 命名以代码为准）。
- `microservice-loan-application` MUST 校验：存在 `ACTIVE` 的 `CREDIT_CONTRACT`（或等价状态机位）才允许进入放款链路相关阶段。

## 6. RocketMQ

- `TOPIC_CREDIT_APPROVED`、`TOPIC_CREDIT_REJECTED`、`TOPIC_CREDIT_MANUAL_REVIEW`、`TOPIC_CREDIT_REVIEW_DECIDED`。
- `TOPIC_CREDIT_CHAT_RISK_ESCALATION`（可选与前者合并为子 tag）。
