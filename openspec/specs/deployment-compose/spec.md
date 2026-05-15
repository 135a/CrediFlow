## Purpose

TBD

## Requirements

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

### Requirement: 本地 Compose 自动加载 APISIX 声明式配置

本地 `docker compose` 编排 MUST 在 **`docker compose up` 的默认可用路径** 下，包含将 **`infra/apisix/`** 中声明式配置 **自动同步至 APISIX 所依赖的 etcd** 的机制（例如独立的 **`apisix-init`** 服务或等价的 **一次性初始化 Job**），使得开发者在 **不手动操作 APISIX Dashboard** 的前提下，完成路由/上游/插件的写入或幂等更新。

#### Scenario: 首次启动后路由可用

- **WHEN** 开发者在干净环境下执行 **`docker compose up -d`**（或项目文档规定的等价命令），且 `etcd-apisix` 与 `apisix` 服务已就绪
- **THEN** 初始化步骤 MUST 在合理超时内完成；随后经 **`9080`** 入口访问受保护路由时，网关 MUST 已按声明式配置生效（例如 JWT 校验与 `X-User-Id` 注入行为可测）

#### Scenario: 重复启动不产生手工双轨依赖

- **WHEN** 开发者多次执行 **`docker compose up`** 而不清理 etcd 卷
- **THEN** 初始化机制 MUST 采用 **幂等写入**（固定资源 `id` 覆盖或等价策略）或文档规定之可重复流程；MUST NOT 要求开发者依赖「仅存在于 Dashboard 的未版本化修改」作为默认路径

### Requirement: 演示级密钥与变量注入的本地可发现性

若声明式配置或 `apisix` 的 `admin_key` 使用 **演示级固定密钥** 并纳入仓库，Compose MUST 通过 **`.env.example` 列出相关变量名**（可在本变更 tasks 阶段与主规格「缺少必需变量启动失败」策略对齐），且文档 MUST 明确标注 **禁止用于生产**；**MUST NOT** 在 compose 中硬编码 **生产** 密钥（与主规格 `环境变量分组与密钥注入` 一致）。

#### Scenario: 新克隆仓库的开发者知道要配什么

- **WHEN** 新开发者克隆仓库并阅读 `deployment-compose` 相关说明
- **THEN** 其 MUST 能从 `.env.example` 或等价清单获知启动 APISIX 初始化所需的变量名（若采用 env 注入 admin key 或 JWT 演示密钥）；若仓库选择完全固定 demo 值，则 MUST 在 `infra/apisix/README.md` 明示风险边界
