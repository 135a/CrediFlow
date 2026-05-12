# CrediFlow 智能小额信贷业务系统

企业级小额信贷**后端**参考实现：Spring Cloud Alibaba 微服务、Apache APISIX 网关、Go 任务调度、Python Data Agent、MySQL / Redis / RocketMQ / Milvus。

> 合规声明：本仓库为**工程与架构演示**，不构成任何金融业务许可或合规结论；投产前须完成法务与监管评估。

## 架构图占位

- 分层示意图：逻辑接入（多端适配，无前端实现）→ **APISIX** → **`app-bff-service`（App）/ `admin-bff-service`（管理端）** → 领域微服务 → Go 调度 → Python Agent → 存储。
- 可将 `docs/architecture.drawio` 或 Excalidraw 导出 PNG 后放到 `docs/images/architecture.png` 并在下文引用：`![architecture](docs/images/architecture.png)`（文件需自行补充）。

## 技术栈

| 层级 | 技术 |
|------|------|
| 网关 | Apache APISIX |
| 微服务 | Spring Boot 3、Spring Cloud 2023、Nacos、OpenFeign、Sentinel（后续任务）、RocketMQ |
| 调度 | Go（`scheduler-go/`） |
| Agent | Python 3.11+（`agent-python/`，LLM 可插拔：通义 / 智谱 / 文心） |
| 数据 | MySQL 8（**单库，不分库分表**）、Redis 7、Milvus |

## 双端 BFF（两个 Maven 模块）

| 模块 | 目录 | 默认端口 | 路径前缀（经网关） | 调用方 |
|------|------|----------|-------------------|--------|
| **管理端** | `admin-bff-service/` | 8090 | `/api/admin/**` | 运营 / 风控 / 管理员 |
| **App 端** | `app-bff-service/` | 8091 | `/api/app/**` | 移动 App / 终端用户 |

领域服务（`user-service` 等）**一套代码**；由网关 + BFF 按路径与 JWT 角色分流。APISIX 路由示例见 `infra/apisix/ROUTES-APP-ADMIN.md`。

- JDK 17、Maven 3.9+
- Docker Desktop（用于 `docker compose`）
- （可选）Go 1.22+、Python 3.11+

## 快速启动中间件

```bash
docker compose up -d mysql redis nacos rmqnamesrv rmqbroker milvus etcd-apisix apisix
```

在宿主机运行 Java 服务时，请将 `application.yml` 中的主机改为 `127.0.0.1` 或使用环境变量覆盖，例如：

```text
MYSQL_HOST=127.0.0.1
NACOS_SERVER=127.0.0.1:8848
ROCKETMQ_NAMESRV=127.0.0.1:9876
REDIS_HOST=127.0.0.1
```

复制 `.env.example` 为 `.env` 并按需填写（勿提交密钥）。

## 构建 Java

```bash
mvn -DskipTests package
```

## 简历版描述（可自行改写）

**项目**：CrediFlow 智能小额信贷后端系统。  
**亮点**：Java + Go + Python 三语言协同；微服务领域拆分；网关统一鉴权与流量安全；MQ 异步解耦；Agent 侧 LLM 可插拔（通义千问 / 智谱 / 文心）+ Milvus RAG；Docker Compose 一键依赖编排。  
**个人职责（占位）**：负责 __________ 服务的领域建模与接口实现 / 网关路由与限流策略 / 调度任务可靠性 / Agent 工具链与白名单安全等。

## OpenSpec

需求与设计见 `openspec/changes/crediflow-micro-credit-system/`。
