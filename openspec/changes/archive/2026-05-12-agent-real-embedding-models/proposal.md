## 背景与原因 (Why)

当前的 AI 数据代理在 RAG（检索增强生成）的知识摄入和检索阶段，使用的是硬编码的模拟数组（`[0.0] * 768`）来代替真实的文本向量（Embeddings）。为了使系统达到生产可用标准，我们必须接入真实的向量模型。此外，为了避免供应商锁定并提供最大的灵活性，向量层必须采用可插拔架构（类似于我们已有的 `llm_adapters`），并同时支持中国主流大模型（如通义千问、智谱、文心）以及国际标准（OpenAI ChatGPT）。

## 变更内容 (What Changes)

- 移除 `rag_graph.py` 和 `app.py` 中所有的模拟向量代码（`[0.0] * 768`）。
- 引入工厂模式和抽象层（`BaseEmbeddingAdapter`），在新建的文件 `embedding_adapters.py` 中实现。
- 针对 OpenAI（如 `text-embedding-3-small`/`text-embedding-ada-002`）、通义千问 (Qwen)、智谱 (Zhipu) 和文心 (Ernie) 实现具体的向量适配器。
- 修改 `/api/v1/knowledge/ingest` 接口，在存入 Milvus 前主动使用配置的供应商对文本进行向量化。
- 更新 `rag_graph.py` 中的 `retrieve_node`，在查询 Milvus 前主动使用配置的供应商对用户提问进行向量化。
- 在 `.env` 和配置中增加对 OpenAI API Key 及所选向量模型供应商的配置项。

## 功能特性 (Capabilities)

### 新增功能特性 (New Capabilities)
- `pluggable-embedding-models`: 核心抽象与多供应商向量模型适配器实现（包含 OpenAI、通义千问、智谱、文心）。

### 修改的功能特性 (Modified Capabilities)
- `agent-python`: 核心的 Python Agent 逻辑将被修改为在知识录入和 RAG 检索阶段强制调用真实的向量模型，而非生成模拟向量。

## 影响范围 (Impact)

- **受影响的代码**: `data-agent/app.py`, `data-agent/rag_graph.py`, `data-agent/config.py`
- **新增代码**: `data-agent/embedding_adapters.py`
- **配置变更**: `.env` 和 `config.py` 将需要新增 `ACTIVE_EMBEDDING_PROVIDER` 配置项；如果选择 OpenAI，则需要相关认证凭据。
- **依赖库**: 将使用官方 SDK 或标准的 `requests` 封装。可能需要更新 `requirements.txt`。
