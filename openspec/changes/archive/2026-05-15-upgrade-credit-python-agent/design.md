## Context

当前位于 `credit/agent-python` 下的 `crediflow_agent/main.py` 是为了验证系统架构连通性而建立的 Mock 接口。其内部各个端点（如 `/manual_review_assistant`、`/chat_intent_risk` 等）的返回数据完全是写死的。为了将架构验证推进到真实的智能化业务层面，我们需要将其替换为实际调用大语言模型（LLM）的逻辑。

## Goals / Non-Goals

**Goals:**
- 在 `requirements.txt` 中引入 `langchain`、`langchain-openai` 等基础大模型库。
- 将 `main.py` 中的端点升级为使用 `PromptTemplate` 和 LLM 进行推理。
- 保证对外暴露的 JSON 响应格式与 Mock 时代完全一致，不破坏上游 Java 服务的解析。

**Non-Goals:**
- 暂不引入复杂的 RAG 向量数据库（Milvus）或 NL2SQL 的实际数据库直连（这需要独立的表结构元数据管理，留待后续迭代）。
- 重点解决：文本分析、风险提取、预警报告生成的纯 Prompt 工程。

## Decisions

- **决议 1: 选择大模型接入方式**
  使用 `langchain-openai` 库作为底层调用引擎，通过环境变量 `OPENAI_API_KEY` 和 `OPENAI_API_BASE` 读取配置，这样不论后续使用 OpenAI 官方，还是国内兼容 OpenAI 协议的模型（如 DeepSeek、通义千问）都能直接无缝切换。

- **决议 2: 端点重构策略**
  - `/manual_review_assistant`: 根据传入的分数与场景，通过 LLM 解析并要求以 JSON 格式输出风险明细与三件套。
  - `/chat_intent_risk`: 传入聊天列表，要求 LLM 分析用户情绪并识别是否含有抗拒/欺诈信号。
  - `/post-loan-warning`: 利用 LLM 直接生成 Markdown 报表文本。

## Risks / Trade-offs

- **延迟增加**：从 Mock 升级到真实 LLM 后，接口响应时间将从几毫秒跃升至秒级。好在架构设计之初这些调用多数已设定为异步或允许高延迟的后台队列执行。
- **结构化输出不稳定性**：LLM 可能会输出不符合严格 JSON 格式的数据。应对策略：使用 Langchain 的 `JsonOutputParser`，并配置模型的格式指令。
