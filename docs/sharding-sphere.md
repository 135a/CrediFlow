# 分库分表：本项目不适用

经产品/架构确认：**CrediFlow 不采用分库分表**，不引入 ShardingSphere 等分片中间件；以 **单库 MySQL** 为权威存储，通过索引、读写分离（若未来需要）与容量规划应对增长。

历史评估草案已废弃，以 `openspec/changes/.../specs/data-storage/spec.md` 中单库要求为准。
