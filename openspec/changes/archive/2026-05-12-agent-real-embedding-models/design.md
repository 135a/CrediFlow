## 场景上下文 (Context)

CrediFlow 的 `data-agent` 负责提供企业级的 AI 风控和 RAG 问答服务。在早期的原型版本中，出于快速跑通 Milvus 流程的目的，我们在 `rag_graph.py` 和 `app.py` 中使用了 `[0.0] * 768` 这样硬编码的零向量作为文本 Embedding 的替代品。
随着系统向生产环境迈进，我们必须将这种模拟向量替换为真实的大模型 Embedding 向量，以实现对业务文档精准的语义检索。同时，由于安全合规以及供应商稳定性的考虑，我们不能强绑定单一厂商，而是需要建立一套可扩展、可插拔的 Embedding 抽象工厂。

## 目标与非目标 (Goals / Non-Goals)

**目标 (Goals):**
- 建立标准的 `BaseEmbeddingAdapter` 抽象类，规范 Embedding 方法入参与出参。
- 实现 OpenAI、通义千问 (Qwen)、智谱 (Zhipu)、文心 (Ernie) 的真实 Embedding 接口接入。
- 打通 `/api/v1/knowledge/ingest` 的真实向量存入管道。
- 打通 `rag_graph.py` 中 `retrieve_node` 的真实向量召回管道。

**非目标 (Non-Goals):**
- 本次不包含对本地私有化部署 Embedding 模型（如通过 Ollama 加载本地模型）的直接实现（但架构需保留扩展可能性）。
- 本次不涉及大规模语料的离线清洗与录入工程。

## 架构决策 (Decisions)

1. **抽象工厂模式**
   我们将在 `embedding_adapters.py` 中定义 `get_active_embedding()` 工厂函数，通过读取环境变量 `ACTIVE_EMBEDDING_PROVIDER` (如果没有配置，则默认复用大模型的 `ACTIVE_PROVIDER`) 决定实例化的具体适配器。
   - **Rationale (原因)**: 这与 `llm_adapters.py` 保持了完全一致的设计模式，降低了系统的认知复杂度和维护成本，使得切换模型仅需改动 `.env` 文件。

2. **向量维度兼容与重构**
   Milvus 在初始创建集合时，将维度硬编码指定为了 768。但不同厂商的默认向量维度不同（例如 OpenAI text-embedding-3-small 支持多维度，ada-002 是 1536 维，文心可能是 384/1024 维，通义通常是 1536 维）。
   - **Rationale (原因)**: 如果强行将不同维度的向量硬塞入同一个集合或强制截断，会严重破坏余弦相似度与欧式距离的数学特性。
   - **决策**: 我们将重构 `MilvusManager`，通过配置项 `config.EMBEDDING_DIMENSION` 动态决定建表维度。当用户在 `.env` 中切换模型时，需要使用配套的维度（例如通义配 1536），以确保精准度。

3. **HTTP 轻量化调用**
   - **决策**: 为了减少繁杂的第三方 SDK 依赖冲突，对于通义、智谱、文心，统一采用标准 `requests` 库通过官方 REST API 进行调用；对于 OpenAI，由于其生态最成熟，可使用官方 `openai` python 库，或者同样采用 requests 发送 HTTP 请求以保持一致。

## 风险与权衡 (Risks / Trade-offs)

- **[风险] 切换模型供应商后导致向量维度不兼容报错** → **缓解措施**: 在 `MilvusManager` 的集合创建中加入对当前维度和现有 Schema 维度的检查，若不匹配需抛出明显错误提示用户清除/重建对应集合。向量数据库不同于关系型数据库，更换 Embedding 模型意味着之前的知识库向量将完全作废，必须重新 Ingest（录入），这属于正常技术权衡。
- **[风险] 接口超时影响主流程** → **缓解措施**: 为各厂商的 HTTP 请求硬性加上合理的 `timeout=10` 配置，并在失败时返回空向量或降级。
