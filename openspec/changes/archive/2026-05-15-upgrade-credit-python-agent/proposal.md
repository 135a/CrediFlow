## Why

在之前的 CrediFlow 架构中，位于 `credit/agent-python` 的 Python AI Agent 仅仅充当了一个微服务调用的 Mock 桩代码。其中涉及的自然语言转 SQL（NL2SQL）、人工审核风险归纳（Manual Review Assistant）、对话意图识别（Chat Intent Risk）以及贷后智能预警（Post Loan Warning）返回的都是硬编码的数据。

随着系统微服务基建趋于稳定，为了真正发挥智能化风控系统的威力，我们亟需将这些 Mock 实现升级为对接真实 LLM（大语言模型）的智能 Agent。

## What Changes

- **引入大模型框架**：在 Python Agent 项目中引入 `langchain` 或相关轻量级大模型 SDK，使其具备 Prompt 编排和 LLM 调用的能力。
- **重构智能接口**：
  - `manual_review_assistant`：通过 LLM 总结用户的风控得分与场景，输出结构化的拒绝或降额建议。
  - `chat_intent_risk`：通过 LLM 提取聊天记录中的抗拒/欺诈意图。
  - `credit_rejection_insight`：通过 LLM 分析风控规则拒绝原因，提供用户友好的解释。
  - `post_loan_warning`：利用 LLM 根据逾期数据（暂且可使用传入的参数）自动生成 Markdown 格式的专业报告。

## Capabilities

### Modified Capabilities

- 信用风控与反欺诈分析机制（Agent-based Risk Control）
- 贷后与还款智能催收

## Impact

- 真正激活系统的“智能化”，提供超越传统规则引擎的上下文风控洞察力。
- Agent API 契约不变，对上游微服务透明。
