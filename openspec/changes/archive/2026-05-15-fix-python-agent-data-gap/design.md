## Context

现有架构中存在两个 Python 模块负责 AI 推理：
- `agent-python`: 拥有真实的 LLM 推理接口且是被 Java 端实际调用的对象，但缺乏容器化，无法部署。
- `data-agent`: 具备完善的容器化、数据库接入（MySQL + Milvus），但在 RAG 和生成环节使用了 Mock 的“存根”返回静态数据，且其接口未与 Java 侧集成。
两者的端口和角色重合，导致当前业务中的 AI 辅助人工审核和拒件解释两大功能陷入瘫痪。必须整合这两个服务。

## Goals / Non-Goals

**Goals:**
- 将 `agent-python` 中用于实际调用的两个接口 (`/manual_review_assistant`, `/credit_rejection_insight`) 的业务逻辑合并到 `data-agent` 中。
- 删除无用的 `/nl2sql` (Mock), `/rag/ask` (Mock), `/post-loan-warning`, `/api/v1/agent/ocr`, `/api/v1/agent/face_verify`。
- 完全废除 `credit/agent-python` 工程。
- 确保 Java 侧对 `/manual_review_assistant` 的 Feign 调用能够路由到新的 `data-agent` 中并返回正常业务数据。

**Non-Goals:**
- 不在此次重构中重写底层的 RAG 生成链，如果时间/复杂度有限，可以将两个接口强行挂载到 `data-agent/app.py` 中，重用 `data-agent` 的环境配置，保留 `agent-python` 的内部逻辑作为独立包存在。
- 不对 Nacos 服务发现进行大规模调整。

## Decisions

- **迁移策略**：为降低风险并快速恢复服务，将 `agent-python` 的核心逻辑（如 `llm_core.py`）复制到 `data-agent` 下的独立目录，如 `legacy_agent/` 中。然后在 `data-agent/app.py` 中 import 这个包并对外暴露上述两个接口。
- **配置一致性**：因为合并后都在同一个 FastAPI 中运行，因此会统一读取 `.env` 中的 `OPENAI_API_KEY` 进行真实推理调用。
- **清理闲置接口**：在 `data-agent/app.py` 中删除无用路由，减少迷惑性代码。

## Risks / Trade-offs

- 合并依赖项可能引发冲突（例如 langchain 和 Pydantic 的版本冲突）。在合并 `requirements.txt` 时需要小心对齐版本。
- Java 端调用的根路径是否完全匹配，需要确保迁移后的 API 路由与 `@FeignClient` 中的声明（没有额外的前缀 `/api/v1`）一致。
