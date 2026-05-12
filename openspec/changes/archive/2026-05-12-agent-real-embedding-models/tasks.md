## 1. 基础配置与依赖准备

- [x] 1.1 在 `data-agent/requirements.txt` 中增加必要的大模型调用依赖（如 `openai`, `requests` 等）。
- [x] 1.2 在 `data-agent/config.py` 和项目的 `.env.example` 中新增 `ACTIVE_EMBEDDING_PROVIDER`、`EMBEDDING_DIMENSION` 以及各模型所需的向量 API Key 配置项。

## 2. 核心抽象与适配器开发

- [x] 2.1 创建 `data-agent/embedding_adapters.py`，定义 `BaseEmbeddingAdapter` 抽象基类，规范输入文本、输出对应维度浮点数组（`list[float]`）的方法接口。
- [x] 2.2 实现 `QwenEmbeddingAdapter`（通义千问）并通过 HTTP 或 SDK 对接其向量 API。
- [x] 2.3 实现 `ZhipuEmbeddingAdapter`（智谱清言）并接入其向量生成接口。
- [x] 2.4 实现 `ErnieEmbeddingAdapter`（文心一言）并接入其向量生成接口。
- [x] 2.5 实现 `OpenAIEmbeddingAdapter`（ChatGPT）并接入 `text-embedding-3-small` 或 `text-embedding-ada-002` 接口。
- [x] 2.6 在 `embedding_adapters.py` 中实现 `get_active_embedding()` 工厂方法，通过读取环境变量动态返回对应的实例对象。

## 3. Milvus 集合维度兼容改造

- [x] 3.1 修改 `data-agent/milvus_manager.py`，将集合创建时的固定维度 `dim=768` 替换为读取 `config.EMBEDDING_DIMENSION` 动态配置。
- [x] 3.2 在 `milvus_manager.py` 中添加集合维度校验逻辑：若当前配置维度与 Milvus 中已存在的集合维度不一致，必须主动抛出异常（报错拦截），防止计算崩溃。

## 4. 业务主流程接入

- [x] 4.1 移除 `data-agent/app.py` 中知识入库接口（`/api/v1/knowledge/ingest`）的 `[0.0] * 768` 模拟数据，改为调用真实 Embedding 适配器获取向量后存入 Milvus。
- [x] 4.2 移除 `data-agent/rag_graph.py` 中检索节点（`retrieve_node`）的模拟向量，改为调用真实的 Embedding 适配器将用户提问转化为向量，再发往 Milvus 查询。
- [x] 4.3 执行完整的 E2E 联调测试，确认更换 `ACTIVE_EMBEDDING_PROVIDER` 和 `EMBEDDING_DIMENSION` 配置时，系统读写行为正确。
