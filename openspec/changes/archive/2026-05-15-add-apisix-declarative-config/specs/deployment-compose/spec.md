# deployment-compose — 本变更规格增量（delta）

> 主规格：`openspec/specs/deployment-compose/spec.md`  
> 本增量仅补充 **本地开发** 中与 APISIX 声明式配置相关的编排要求；不放宽「生产密钥不得硬编码」等既有约束。

## ADDED Requirements

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
