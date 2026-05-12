## ADDED Requirements

### Requirement: LLM 可插拔适配器与单活提供者

Python Data Agent MUST 通过稳定抽象接口调用大模型；MUST 同时内置通义千问、智谱清言、文心一言的适配器实现；运行时 MUST 仅激活单一 `ACTIVE_PROVIDER`；API 密钥 MUST 仅从环境变量或外部密钥管理注入。

#### Scenario: 切换提供者需重启或热切换配置

- **WHEN** 运维将 `ACTIVE_PROVIDER` 从 `qwen` 切换为 `zhipu`
- **THEN** 系统 MUST 在配置生效后仅使用新提供者的端点与计费维度且 MUST 记录切换审计

### Requirement: RAG 与 Milvus 检索审计

RAG 管道 MUST 使用 Milvus 作为向量检索后端；回答 MUST 附带引用片段的 `source_id` 列表；检索与生成 MUST 记录模型名、提示词版本与 request id。

#### Scenario: 无引用则降级

- **WHEN** 检索结果为空或相似度低于阈值
- **THEN** 系统 MUST 明确返回「知识库未覆盖」且 MUST NOT 伪造条款引用

### Requirement: NL2SQL 只读与安全约束

NL2SQL MUST 仅连接只读数据库角色；MUST 对生成 SQL 进行静态分析与表白名单校验；查询结果 MUST 限制最大行数并对敏感列掩码。

#### Scenario: 生成写语句被拒绝

- **WHEN** NL2SQL 输出包含 `INSERT`/`UPDATE`/`DELETE` 等写操作关键字
- **THEN** 系统 MUST 拒绝执行并 MUST 记录安全事件

### Requirement: NL2API 白名单

NL2API MUST 仅允许调用已登记的后台 API 白名单；参数 MUST 通过 schema 校验；禁止开放任意 URL 调用。

#### Scenario: 未登记 API 被拒绝

- **WHEN** 解析出的目标 API 不在白名单
- **THEN** 系统 MUST 拒绝执行并 MUST 返回可诊断错误码

### Requirement: 不参与核心事务与终局裁决

Agent MUST NOT 直接执行放款、扣款、授信通过等终局动作；此类动作 MUST 由 Java 服务基于规则与权限完成。

#### Scenario: 终局动作请求被拦截

- **WHEN** Agent 工具链尝试调用未在白名单或标注为高危的写接口
- **THEN** 编排层 MUST 拒绝并 MUST 记录拦截原因
