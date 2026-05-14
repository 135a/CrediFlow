# Tasks：授信 9 步 + Agent 治理

> Agent 相关任务 MUST 可在开关关闭时跳过，主链路仍可通过验收。

## Phase 0：OpenSpec / 契约骨架

- [x] 0.1 评审并冻结 `design.md` 状态枚举与表名；冲突以 `credit-application-lifecycle` 为准同步下游
- [x] 0.2 `crediflow-common`：新增 `Credit*Event` DTO + `MqConstants.TOPIC_CREDIT_*`（含对话升级 topic 或 tag）
- [x] 0.3 Flyway 草案：`cf_credit_score`、`cf_user_credit_quota`、`cf_credit_review_queue`、`cf_credit_risk_escalation`（或合并表）

## Phase 1：评分与硬规则（无 Agent）

- [x] 1.1 `credit-risk-service`：硬规则管线（黑名单 / 逾期 / 账号状态），拒绝短理由码 + 审计详情
- [x] 1.2 实现 S1~S4 计算器（初版可用占位数据源 + TODO 标记外部征信）
- [x] 1.3 `WeightedScoreEngine` + `RiskLevelClassifier`（阈值 Nacos）
- [x] 1.4 落库 `cf_credit_score` 与申请单关联

## Phase 2：基础路由与二次人脸（Java 死板路径）

- [x] 2.1 扩展 `CreditApplication` 状态机与 Mapper
- [x] 2.2 对接 `user-service`：`bizScene=CREDIT_SECONDARY_FACE` 提交与回调关联 `applicationId`
- [x] 2.3 MEDIUM / HIGH 基础路径联调（Agent 关闭）

## Phase 3：循环额度 + 授信合同

- [x] 3.1 额度线性公式 + 写 `cf_user_credit_quota`
- [x] 3.2 `loan-contract-service`：`CREDIT_CONTRACT` 生成与签署状态机
- [x] 3.3 `loan-application-service`：增加授信合同 ACTIVE 前置

## Phase 4：BFF 与 APP 查询

- [x] 4.1 `app-bff-service`：`/api/app/credit/apply|status|quota|last-result`
- [x] 4.2 错误码映射：不透出内部规则码

## Phase 5：人工审核辅助（Agent 自动化三件套）

- [x] 5.1 `agent-python`：实现 `manual_review_assistant` 工具，输入用户特征和评分明细，自动输出结构化的三件套（具体风险明细、量化违约概率/欺诈概率、四种审核建议之一）
- [x] 5.2 `credit-risk-service`：在申请单状态变更为 `PENDING_MANUAL_REVIEW` 后，异步调用 Agent 并将「三件套」结果结构化落库至审核记录中
- [x] 5.3 `credit-admin-ops`（管理台）：在人工审核详情页，明确展示 AI 给出的风险明细、双维概率及操作建议

## Phase 6：对话意图风控升级

- [x] 6.1 对话服务（`rag-customer-service` 或 BFF）调用 Agent 的 `chat_intent_risk` 工具，实时判断是否表达了不想还款、逃废债等意图
- [x] 6.2 Agent 构建包含 `relevantChatLogs` 和 `agentSuggestions` 的 `ChatRiskEscalationSignal`，投递至 `credit-risk-service` 内部 API
- [x] 6.3 `credit-risk-service`：收到风险上报后，**仅将其加入人工审核队列**（`cf_credit_review_queue`），供审核台展示聊天记录和建议，**不得执行直接干预动作**

## Phase 7：拒绝洞察

- [x] 7.1 Agent `credit_rejection_insight`：输入子规则命中摘要，输出用户安全文案 + 可执行建议
- [x] 7.2 `credit-risk-service`：拒绝时写 `risk_insight`；`last-result` 接口返回 `userSafeInsight`
- [x] 7.3 管理端：可查询原始 `risk_insight`（脱敏）

## Phase 8：联调与灰度

- [x] 8.1 端到端：绑卡后授信 → LOW 直过 / MEDIUM 二次人脸 / HIGH 人工
- [x] 8.2 对话触发升级 → 下一次授信强制二次人脸或额度 cap 验证
- [x] 8.3 压测：Agent 超时路径不得阻塞授信主链路超过 SLA
