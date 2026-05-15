## 1. 环境与依赖升级

- [x] 1.1 在 `credit/agent-python/requirements.txt` 中增加大模型开发依赖库：`langchain`, `langchain-openai`, `pydantic` 等。

## 2. LLM 核心工具类封装

- [x] 2.1 在 `crediflow_agent` 包下新增 `llm_core.py`（或其他合适的文件），封装初始化 LLM 的通用函数，支持从环境变量（如 `OPENAI_API_KEY`）加载模型实例，并统一设置 temperature 和模型版本。

## 3. 端点服务智能化重构

- [x] 3.1 改造 `main.py` 中的 `/manual_review_assistant` 端点，引入 PromptTemplate，使用 LLM 动态分析 `scoreDetail` 与 `sceneType`，并利用 Pydantic 约束使其输出严谨的 `riskDetails`、`defaultProbability` 和 `suggestion`。
- [x] 3.2 改造 `/chat_intent_risk` 端点，将硬编码的关键字匹配替换为基于 LLM 的文本情感与意图分类。
- [x] 3.3 改造 `/credit_rejection_insight` 端点，通过 LLM 为拒绝理由生成具有“管理员视角”和“安全提示视角”的高质量文本。
- [x] 3.4 改造 `/post-loan-warning` 端点，通过 LLM 生成结构化、Markdown 格式的贷后风险分析长文报告。

## 4. 验证

- [x] 4.1 确保重构后的 `main.py` 没有语法错误，并启动服务验证。
