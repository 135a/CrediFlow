## MODIFIED Requirements

### Requirement: RAG 与 Milvus 检索审计

RAG 管道 MUST 使用 Milvus 作为向量检索后端，并且 MUST 在摄入数据和用户查询时调用真实的 Embedding 供应商接口进行向量化，严格禁止在生产阶段使用模拟向量数据；回答 MUST 附带引用片段的 `source_id` 列表；检索与生成 MUST 记录模型名、提示词版本与 request id。

#### Scenario: 无引用则降级

- **WHEN** 检索结果为空或相似度低于阈值
- **THEN** 系统 MUST 明确返回「知识库未覆盖」且 MUST NOT 伪造条款引用
