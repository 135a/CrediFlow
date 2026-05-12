# Milvus 向量库集成策略 (11.2)

## 1. 集合设计 (Collection Schema)
在 CrediFlow 中，我们使用 `crediflow_knowledge` 集合存储业务知识库（如：放款政策、风控规则），其 Schema 设计如下：
- `id` (INT64, Primary Key, AutoID)
- `source_id` (VARCHAR): 溯源标识，用于在回答时返回给客户端（11.3 审计要求）。
- `embedding` (FLOAT_VECTOR, dim=768): 知识分块的向量。
- `content` (VARCHAR): 原始文本内容。

## 2. 写入与更新管道
- **新增 (Insert)**: 调用 `MilvusManager.insert()` 传入提取的文本与 `sentence-transformers` 等模型计算出的向量。
- **更新/删除策略**: Milvus 对精准更新支持有限。对于业务知识更新，策略为：基于 `source_id` 软删除（或借助 Partition 删除），然后重新插入新版本的 Embedding 数据。

## 3. 检索配置
使用 `IVF_FLAT` 索引结构与 `L2` 距离进行快速近似最近邻 (ANN) 检索。每次 RAG 提问前检索 Top-3 的上下文片段作为 prompt 背景。
