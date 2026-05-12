## ADDED Requirements

### Requirement: Compose 一键编排目标

项目 MUST 提供 Docker Compose 编排描述，使开发/演示环境可通过单命令启动依赖与核心服务；MUST 定义服务启动顺序依赖（例如：数据库先于应用）。

#### Scenario: 启动成功健康检查

- **WHEN** 执行 `docker compose up -d` 且镜像可用
- **THEN** 关键容器 MUST 通过健康检查进入 healthy 状态或 MUST 提供等价就绪探针

### Requirement: 网络分区 edge 与 internal

Compose MUST 将对外暴露的服务置于 `edge` 网络（至少包含 APISIX）；数据库、消息队列、Milvus、内部微服务 MUST 仅连接 `internal` 网络且 MUST NOT 对宿主机外网直接暴露端口（除非文档明确为演示需要并附带风险提示）。

#### Scenario: MySQL 不暴露公网

- **WHEN** 使用默认演示 compose 配置
- **THEN** MySQL MUST 仅绑定在 internal 网络可达地址

### Requirement: 环境变量分组与密钥注入

敏感配置 MUST 通过环境变量或 Docker secrets 注入；`compose.yaml` MUST NOT 硬编码生产密钥；MUST 提供 `.env.example` 列出必需变量名（实现阶段在 tasks 落地）。

#### Scenario: 缺少必需变量启动失败

- **WHEN** 启动应用容器但未设置数据库连接串
- **THEN** 容器 MUST 启动失败并 MUST 输出缺失变量名
