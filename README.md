<div align="center">

# 🏦 CrediFlow

**智能小额信贷业务系统**

*Enterprise-grade Micro-Credit Backend System with AI-powered Risk Intelligence*

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Go](https://img.shields.io/badge/Go-1.22+-00ADD8?logo=go)](https://go.dev/)
[![Python](https://img.shields.io/badge/Python-3.11+-3776AB?logo=python)](https://python.org/)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023-6DB33F?logo=spring)](https://spring.io/projects/spring-cloud)
[![APISIX](https://img.shields.io/badge/Apache%20APISIX-3.9-E8433E?logo=apache)](https://apisix.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[架构总览](#-架构总览) · [快速启动](#-快速启动) · [项目结构](#-项目结构) · [技术栈](#%EF%B8%8F-技术栈) · [核心特性](#-核心特性) · [API 文档](#-api-文档) · [部署指南](#-部署指南)

</div>

---

## 📖 项目简介

CrediFlow 是一套面向小额信贷场景的**企业级全链路后端系统**，融合 **Java 微服务 + Go 分布式调度 + Python AI Agent** 三语言协同架构，覆盖从用户注册、授信评估、贷款申请、合同签署、分期还款到贷后风控的完整信贷业务生命周期。

系统以 **AI 驱动的智能风控决策** 为核心差异化能力 —— 内置的 Data Agent 通过 ReAct 推理模式，自主调用 RAG 知识库检索、NL2SQL 历史数据查询和 NL2API 外部征信接口，最终由大模型进行综合裁决，输出结构化的授信建议。

> ⚠️ **合规声明**：本仓库为工程与架构演示项目，不构成任何金融业务许可或合规结论。投产前须完成法务与监管评估。

## 🏗 架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        逻辑接入层 (多端适配)                          │
│                    App 用户端  ·  Web 后台管理端                      │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTPS
┌──────────────────────────▼──────────────────────────────────────────┐
│                    Apache APISIX 云原生网关                          │
│         JWT 鉴权 · 限流熔断 · IP 黑白名单 · 灰度路由                  │
└─────────┬───────────────────────────────────────────┬──────────────┘
          │                                           │
  ┌───────▼────────┐                         ┌────────▼───────┐
  │  app-bff (8091) │                         │ admin-bff(8090)│
  │   App 端聚合     │                         │  管理端聚合     │
  └───────┬────────┘                         └────────┬───────┘
          │              OpenFeign / REST              │
┌─────────▼───────────────────────────────────────────▼──────────────┐
│                  Spring Cloud Alibaba 微服务集群                     │
│                                                                     │
│  ┌──────────┐ ┌──────────────┐ ┌────────────┐ ┌──────────────────┐ │
│  │ 用户服务  │ │ 授信风控服务  │ │ 贷款申请    │ │ 借款合同服务     │ │
│  │user      │ │credit-risk   │ │loan-app    │ │loan-contract     │ │
│  └──────────┘ └──────────────┘ └────────────┘ └──────────────────┘ │
│  ┌──────────┐ ┌──────────────┐ ┌────────────┐ ┌──────────────────┐ │
│  │ 还款分期  │ │ 贷后逾期管理  │ │ 资金流水    │ │ 系统权限后台     │ │
│  │repayment │ │post-loan     │ │fund-flow   │ │system            │ │
│  └──────────┘ └──────────────┘ └────────────┘ └──────────────────┘ │
└─────────┬───────────────┬───────────────────────────┬──────────────┘
          │               │ RocketMQ (异步)            │ HTTP
  ┌───────▼────────┐ ┌────▼──────────┐     ┌──────────▼──────────┐
  │  Go 调度服务    │ │  Nacos        │     │ Python Data Agent   │
  │  batch-service │ │  注册/配置中心  │     │  AI 智能决策大脑     │
  │  ─────────     │ └───────────────┘     │  ──────────────     │
  │  · 自动代扣    │                       │  · RAG 知识问答     │
  │  · 逾期巡检    │                       │  · NL2SQL 数据查询  │
  │  · 罚息计算    │                       │  · NL2API 接口桥接  │
  │  · 还款提醒    │                       │  · ReAct 风控裁决   │
  └───────┬────────┘                       └──────────┬──────────┘
          │                                           │
┌─────────▼───────────────────────────────────────────▼──────────────┐
│                          数据存储层                                  │
│     MySQL 8 (业务主库)  ·  Redis 7 (缓存/分布式锁)                   │
│     Milvus (向量检索)   ·  RocketMQ (消息队列)                       │
└─────────────────────────────────────────────────────────────────────┘
```

## ⚡ 快速启动

### 前置条件

- Docker Desktop（含 Docker Compose v2）
- JDK 17 + Maven 3.9+
- Go 1.22+（编译调度服务）
- Python 3.11+（运行 Data Agent）

### 1. 克隆仓库

```bash
git clone https://github.com/your-org/CrediFlow.git
cd CrediFlow
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入你的大模型 API Key
# ACTIVE_PROVIDER=qwen          # 可选: qwen / zhipu / ernie / openai
# ACTIVE_EMBEDDING_PROVIDER=qwen
# QWEN_API_KEY=sk-xxx
```

### 3. 一键启动全部服务

```bash
# 启动基础设施 + 全部业务微服务
docker compose up -d
```

> 💡 **首次构建**需要拉取镜像和编译 Java/Go/Python 镜像，预计 10-15 分钟。后续启动约 30 秒。

### 4. 仅启动基础设施（本地开发模式）

```bash
# 仅启动中间件，Java 服务在 IDE 中启动
docker compose up -d mysql redis nacos rmqnamesrv rmqbroker milvus etcd-apisix apisix
```

本地 IDE 开发时设置环境变量：

```properties
MYSQL_HOST=127.0.0.1
NACOS_SERVER=127.0.0.1:8848
ROCKETMQ_NAMESRV=127.0.0.1:9876
REDIS_HOST=127.0.0.1
```

### 5. 构建 Java 服务

```bash
mvn -DskipTests clean package
```

## 📁 项目结构

```
CrediFlow/
├── user-service/              # 用户服务 - 注册/登录/认证/画像
├── credit-risk-service/       # 授信风控 - 额度评估/风控规则/AI Agent 对接
├── loan-application-service/  # 贷款申请 - 申请提交/资料审核/状态流转
├── loan-contract-service/     # 借款合同 - 电子合同生成/签署/归档
├── repayment-service/         # 还款分期 - 还款计划/主动还款/分期管理
├── post-loan-service/         # 贷后管理 - 逾期判定/罚息计算/催收任务
├── fund-flow-service/         # 资金流水 - 放还款流水/对账校验/统计
├── system-service/            # 系统后台 - 角色/权限/操作日志审计
├── app-bff-service/           # App 端 BFF 聚合层
├── admin-bff-service/         # 管理端 BFF 聚合层
├── crediflow-common/          # 公共模块 - 统一响应/异常/工具类
│
├── batch-service/             # [Go] 分布式任务调度服务
│   ├── main.go
│   └── Dockerfile
│
├── data-agent/                # [Python] AI Data Agent 智能决策服务
│   ├── app.py                 # FastAPI 入口
│   ├── rag_graph.py           # LangGraph RAG 检索-生成流程
│   ├── embedding_adapters.py  # 可插拔 Embedding 适配器 (Qwen/Zhipu/Ernie/OpenAI)
│   ├── llm_adapters.py        # 可插拔 LLM 适配器
│   ├── nl2sql.py              # NL2SQL 安全引擎 (只读/白名单/PII 脱敏)
│   ├── nl2api.py              # NL2API 白名单网关
│   ├── milvus_manager.py      # Milvus 向量库管理 (动态维度)
│   ├── config.py              # 统一配置
│   └── Dockerfile
│
├── infra/                     # 基础设施配置
│   └── apisix/                # APISIX 网关路由与插件配置
├── observability/             # 可观测性 (Prometheus/Grafana)
├── docs/                      # 文档资料
│   ├── openapi.json           # OpenAPI 3.0 接口规范
│   ├── e2e-test-checklist.md  # 端到端测试清单
│   └── ...
│
├── docker-compose.yml         # 全量编排 (基础设施 + 10 个业务服务)
├── Dockerfile.java            # Java 微服务统一多阶段构建
├── .env.example               # 环境变量模板
└── pom.xml                    # Maven 父 POM
```

## 🛠️ 技术栈

| 层级 | 技术 | 用途 |
|:-----|:-----|:-----|
| **网关** | Apache APISIX 3.9 | 统一入口、JWT 鉴权、限流熔断、灰度路由、安全防护 |
| **微服务框架** | Spring Boot 3.2 + Spring Cloud 2023 | 业务基座 |
| **服务治理** | Nacos 2.3 | 服务注册发现 + 配置中心 |
| **远程调用** | OpenFeign | 声明式 HTTP 客户端 |
| **ORM** | MyBatis-Plus | 数据持久化 |
| **消息队列** | Apache RocketMQ 5.2 | 异步解耦、事件驱动 |
| **任务调度** | Go batch-service | 分布式定时任务（代扣/巡检/罚息） |
| **AI Agent** | Python FastAPI + LangGraph | RAG / NL2SQL / NL2API / ReAct 推理 |
| **LLM** | 通义千问 / 智谱清言 / 文心一言 / OpenAI | 可插拔多供应商大模型 |
| **Embedding** | Qwen / Zhipu / Ernie / OpenAI | 可插拔多供应商向量模型 |
| **向量数据库** | Milvus 2.3 | RAG 知识库语义检索 |
| **关系数据库** | MySQL 8.0 | 核心业务数据 (单库) |
| **缓存** | Redis 7 | 热点缓存 + 分布式锁 |
| **可观测性** | Prometheus + Grafana + JSON 结构化日志 | 监控、告警、链路追踪 |
| **部署** | Docker Compose | 一键编排 |

## 🌟 核心特性

### 🔄 信贷全生命周期

覆盖完整的小额信贷业务链路，每个环节均为独立微服务：

```
用户注册 → 实名认证 → 授信评估 → 贷款申请 → 合同签署 → 放款 → 分期还款 → 贷后管理
```

### 🤖 AI 智能风控（ReAct 四步决策）

Data Agent 作为系统的"智能审查员"，采用 ReAct 模式自主完成风控裁决：

```
Step 1: 查政策 (RAG)     → Milvus 向量检索企业风控知识库
Step 2: 查历史 (NL2SQL)   → 只读 MySQL 查询用户逾期记录
Step 3: 查征信 (NL2API)   → 桥接内部芝麻信用分/黑名单接口
Step 4: 综合裁决 (LLM)    → 大模型逻辑推理，输出 JSON 格式授信建议
```

### 🔌 多供应商可插拔适配

LLM 与 Embedding 均支持通过环境变量热切换，无需修改代码：

| 能力 | 支持的供应商 | 配置项 |
|:-----|:-----------|:------|
| 大语言模型 | 通义千问 · 智谱清言 · 文心一言 | `ACTIVE_PROVIDER` |
| 向量模型 | 通义 · 智谱 · 文心 · OpenAI | `ACTIVE_EMBEDDING_PROVIDER` |

### 🛡️ 金融级安全设计

- **幂等保障**：Redis 分布式锁 + 幂等 Token，防止重复放款/重复还款
- **数据脱敏**：身份证、手机号加密存储，NL2SQL 查询结果自动 PII 掩码
- **操作审计**：全链路操作日志留存，满足金融监管可追溯要求
- **Agent 零信任**：SQL 静态分析拦截写操作、API 白名单准入、表级白名单过滤

### ⏱️ Go 高并发任务调度

| 定时任务 | 说明 |
|:---------|:-----|
| 自动代扣 | 按还款计划发起代扣，联动还款服务 |
| 逾期巡检 | 扫描逾期订单、更新状态、触发催收 |
| 罚息计算 | 按日计算逾期罚息，同步资金流水 |
| 还款提醒 | 到期前推送提醒通知 |

所有调度任务通过内部 HTTP API 执行状态变更，**严禁直接写数据库**，确保事务边界清晰。

## 📋 API 文档

完整的 OpenAPI 3.0 规范文件位于 [`docs/openapi.json`](docs/openapi.json)。

### 核心接口一览

| 接口 | 方法 | 说明 |
|:-----|:-----|:-----|
| `/api/app/user/register` | POST | 用户注册 |
| `/api/app/user/login` | POST | 用户登录 (返回 JWT) |
| `/api/app/credit/apply` | POST | 申请授信额度 |
| `/api/app/loan/apply` | POST | 提交贷款申请 |
| `/api/app/contract/sign` | POST | 签署电子合同 |
| `/api/app/repayment/generate` | POST | 生成还款计划 |
| `/api/app/repayment/active-repay` | POST | 主动还款 |
| `/api/v1/agent/rag` | POST | RAG 知识问答 |
| `/api/v1/agent/nl2sql` | POST | 自然语言查数据 |
| `/api/v1/agent/nl2api` | POST | 自然语言调接口 |
| `/api/v1/credit/evaluate` | POST | AI 风控综合裁决 |
| `/api/v1/knowledge/ingest` | POST | 知识库文档录入 |

## 🚀 部署指南

### Docker Compose 全量部署

`docker-compose.yml` 包含以下全部服务，通过健康检查确保启动顺序：

| 分类 | 服务 |
|:-----|:-----|
| **基础设施** | MySQL · Redis · Nacos · RocketMQ · Milvus · etcd · APISIX |
| **Java 微服务** | user · credit-risk · loan-application · loan-contract · repayment · post-loan · fund-flow |
| **Go 服务** | batch-service |
| **Python 服务** | data-agent |

### 网络隔离

| 网络 | 用途 | 成员 |
|:-----|:-----|:-----|
| `edge` | 对外暴露 | APISIX · etcd |
| `internal` | 内部通信 | 所有微服务 · 数据库 · 消息队列 |

### 知识库初始化

系统部署后，需要将风控合规文档录入 Milvus 向量库：

```bash
# 调用知识录入接口
curl -X POST http://localhost:8000/api/v1/knowledge/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "source_id": "policy_001",
    "content": "25 岁以下年轻群体，月收入低于 5000 元，放款额度上限为 5000 元..."
  }'
```

实际生产中，应编写离线脚本批量读取 Word/PDF 版《风控合规操作手册》，按段落切分后逐条调用此接口。

## 📄 文档索引

| 文档 | 说明 |
|:-----|:-----|
| [`docs/openapi.json`](docs/openapi.json) | OpenAPI 3.0 接口规范 |
| [`docs/e2e-test-checklist.md`](docs/e2e-test-checklist.md) | 端到端测试清单 |
| [`docs/nacos-convention.md`](docs/nacos-convention.md) | Nacos 配置规范 |
| [`docs/observability-metrics.md`](docs/observability-metrics.md) | 可观测性指标定义 |
| [`docs/db-access.md`](docs/db-access.md) | 数据库访问规范 |
| [`docs/milvus-strategy.md`](docs/milvus-strategy.md) | Milvus 向量库策略 |
| [`infra/apisix/`](infra/apisix/) | APISIX 路由与插件配置 |

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/awesome-feature`
3. 提交更改：`git commit -m 'feat: add awesome feature'`
4. 推送分支：`git push origin feature/awesome-feature`
5. 提交 Pull Request

## 📝 License

本项目基于 [MIT License](LICENSE) 开源。

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐ Star！**

</div>
