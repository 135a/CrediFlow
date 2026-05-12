## ADDED Requirements

### Requirement: Embedding 可插拔抽象层与多供应商支持

系统 MUST 提供基于工厂模式的 `BaseEmbeddingAdapter` 抽象层，用于将文本转换为向量；MUST 至少支持 OpenAI、通义千问 (Qwen)、智谱 (Zhipu) 以及文心 (Ernie) 的 API 调用；MUST 根据环境变量 `ACTIVE_EMBEDDING_PROVIDER`（若无则回退使用 `ACTIVE_PROVIDER`）动态切换供应商。

#### Scenario: 动态切换 Embedding 提供商

- **WHEN** 管理员修改 `ACTIVE_EMBEDDING_PROVIDER` 配置并重新启动应用
- **THEN** 系统 MUST 在后续的知识入库与 RAG 检索环节，一致地调用新配置的供应商模型生成真实向量，且 MUST NOT 报错。

### Requirement: Milvus Schema 的维度兼容

Milvus 集合（Collection）的维度设定 MUST 通过外部配置（如 `EMBEDDING_DIMENSION`）动态决定，以兼容不同模型默认输出的维度大小（例如 768 或 1536 维）；系统 MUST 在写入或查询前确保向量维度与 Schema 维度一致，以防止余弦相似度等距离计算失效。

#### Scenario: 维度不匹配时报错并拒绝执行

- **WHEN** 当前配置的 Embedding 模型输出为 1536 维，但系统检测到目标 Milvus 集合创建时的维度设定为 768
- **THEN** 系统 MUST 拒绝向量入库或检索操作，抛出明显的维度不匹配错误，并提示操作员需重建对应集合。
