# Python Agent 双模块数据流与业务对接分析

> 分析日期：2026-05-15
> 分析范围：`credit/agent-python/` + `credit/data-agent/` 与 Java `credit-risk-service` 的集成状态

---

## 一、两个模块概览

```
credit/
├── agent-python/          ← 原始版（LLM 驱动的 AI 助手，LangChain + ChatOpenAI）
│   └── crediflow_agent/
│       ├── main.py        ← FastAPI :8000，6 个端点
│       └── llm_core.py    ← LLM 调用封装
│
└── data-agent/            ← 演进版（RAG + NL2SQL + NL2API + ReAct 编排）
    ├── app.py             ← FastAPI :8000，7 个端点
    ├── config.py          ← 环境变量配置（.env）
    ├── nl2sql.py          ← MySQL 只读 SQL 执行引擎
    ├── nl2api.py          ← 白名单 HTTP API 调用引擎
    ├── rag_graph.py       ← LangGraph RAG 管道（检索 + 生成）
    ├── milvus_manager.py  ← Milvus 向量存储管理
    ├── llm_adapters.py    ← 多供应商 LLM 适配器（Qwen/智谱/文心）
    ├── embedding_adapters.py ← 多供应商嵌入模型适配器
    ├── Dockerfile
    └── requirements.txt
```

### 谁是"真的"？

| 维度 | agent-python | data-agent |
|------|-------------|------------|
| Docker 容器化 | ❌ 无容器定义 | ✅ docker-compose 中有 data-agent 服务 |
| Nacos 注册 | ❌ 未注册 | ✅ 通过 Docker 网络可达 |
| Java Feign 目标 | ✅ AgentClient 调用的就是它的端点 | ❌ 端点不被 Java 调用 |
| 数据库直连 | ❌ 无 | ✅ MySQL 只读 + Milvus |
| LLM 调用 | ✅ 真实（langchain-openai） | ⚠️ 适配器返回静态字符串（存根） |
| 端口 | 8000 | 8000 |
| 结论 | 接口对、部署缺 | 部署对、接口偏、LLM 空 |

**两者端口冲突，只能部署一个。**

---

## 二、agent-python 数据流

### 2.1 架构

```
                            Feign (Nacos: agent-service)
  ┌──────────────────────────┐                              ┌─────────────────┐
  │ credit-risk-service/Java │  POST /manual_review_        │ agent-python    │
  │                          │        assistant             │ (Python :8000)  │
  │ CreditServiceImpl ───────┼─────────────────────────────▶│                 │
  │                          │  {userId, sceneType,          │ LangChain LLM   │
  │                          │   scoreDetail}                │   ↓             │
  │                          │◀─────────────────────────────│ 推理 → 结果     │
  │                          │  {riskDetails,                │                 │
  │ ManualReviewAsyncService │   defaultProbability,         │                 │
  │ (异步) ──────────────────┼─────────────────────────────▶│                 │
  │                          │  POST /credit_rejection_      │                 │
  │                          │        insight               │                 │
  │                          │  {ruleSummaries}              │                 │
  │                          │◀─────────────────────────────│                 │
  │                          │  {userSafeInsight,            │                 │
  │                          │   adminInsight}               │                 │
  └──────────────────────────┘                              └─────────────────┘
```

### 2.2 端点清单

| HTTP 方法 | 路径 | 数据来源 | 调用方 | 状态 |
|-----------|------|---------|--------|------|
| `GET` | `/health` | 无（静态返回） | 健康检查 | ✅ |
| `POST` | `/nl2sql` | 无（硬编码 mock） | — | ❌ Mock |
| `POST` | `/post-loan-warning` | HTTP 请求体（overdueCount 等） | — | ❌ 无人调用 |
| `POST` | `/rag/ask` | 无（硬编码 mock） | — | ❌ Mock |
| `POST` | `/manual_review_assistant` | HTTP 请求体（userId, scoreDetail, sceneType） | **Java AgentClient** | ⚠️ 接口对，但 agent-python 没容器化 |
| `POST` | `/chat_intent_risk` | HTTP 请求体（userId, chatLogs） | — | ❌ 无人调用 |
| `POST` | `/credit_rejection_insight` | HTTP 请求体（ruleSummaries） | **Java AgentClient** | ⚠️ 接口对，但 agent-python 没容器化 |

### 2.3 与 Java 的双向通信

**Java → Python（正向）**：

```java
// AgentClient.java — Spring Cloud OpenFeign
@FeignClient(name = "agent-service", fallback = AgentClientFallback.class)
public interface AgentClient {
    @PostMapping("/manual_review_assistant")
    ManualReviewAssistantResponse manualReviewAssistant(
        @RequestBody ManualReviewAssistantRequest request);

    @PostMapping("/credit_rejection_insight")
    CreditRejectionInsightResponse creditRejectionInsight(
        @RequestBody CreditRejectionInsightRequest request);
}
```

调用时机：
1. `CreditServiceImpl.applyCredit()` — 硬规则拒绝后，调用 `creditRejectionInsight` 获取拒绝解释
2. `ManualReviewAsyncService` — 申请进入人工审核队列时，异步调用 `manualReviewAssistant` 生成风险评估

**Python → Java（反向）**：

`/chat_intent_risk` 端点检测到风险时，回调 Java：
```python
requests.post("http://localhost:8080/api/internal/credit/risk-signal/escalate", json=signal)
```
但此端点**无人调用**，且 URL **硬编码为 localhost:8080**。

### 2.4 数据来源

agent-python **不查询任何数据库**。所有数据来自：
- Java Feign 调用的 HTTP POST 请求体
- LLM API（langchain-openai / ChatOpenAI）

### 2.5 问题

1. **没有容器化**: docker-compose 中无此服务，无法被 Nacos 发现
2. **Java 通过 Nacos 找 `agent-service`**，但 agent-python 未注册，请求无法到达
3. **4/7 端点是 Mock 或无人调用**
4. **反向调用 URL 硬编码 localhost**，容器化后必失效

---

## 三、data-agent 数据流

### 3.1 架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                        data-agent (Python :8000)                     │
│                                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌───────────────┐  │
│  │  RAG     │    │ NL2SQL   │    │ NL2API   │    │  ReAct 编排    │  │
│  │ Milvus   │    │ MySQL    │    │ Java HTTP│    │ RAG+SQL+API   │  │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘    └───────┬───────┘  │
│       │               │               │                   │          │
│       ▼               ▼               ▼                   ▼          │
│  ┌─────────┐    ┌──────────┐    ┌───────────┐    ┌─────────────┐    │
│  │ Milvus  │    │MySQL(只读)│    │Java HTTP  │    │ LLM 适配器   │    │
│  │向量检索  │    │crediflow  │    │内部API    │    │ (存根!)     │    │
│  └─────────┘    └──────────┘    └───────────┘    └─────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 端点清单

| HTTP 方法 | 路径 | 数据来源 | 调用方 | 状态 |
|-----------|------|---------|--------|------|
| `POST` | `/api/v1/knowledge/ingest` | HTTP 请求体 → Milvus | — | ⚠️ 需先手动灌入知识 |
| `POST` | `/api/v1/agent/rag` | Milvus 向量检索 + LLM | — | ⚠️ LLM 适配器是存根 |
| `POST` | `/api/v1/agent/nl2sql` | MySQL 只读查询 | — | ✅ 可独立运行 |
| `POST` | `/api/v1/agent/nl2api` | 白名单 HTTP → Java | — | ✅ 但只映射 1 个 API |
| `POST` | `/api/v1/credit/evaluate` | RAG + NL2SQL + NL2API → LLM 裁决 | batch-service RiskDispatchJob | ❌ LLM 存根 + 送空数据 |
| `POST` | `/api/v1/agent/ocr` | HTTP 请求体 | — | ❌ Mock |
| `POST` | `/api/v1/agent/face_verify` | HTTP 请求体 | — | ❌ Mock |

### 3.3 数据来源详解

#### 3.3.1 MySQL（只读）

```
连接: DB_URI_RO = mysql+pymysql://readonly_user:password@localhost:3306/crediflow

白名单表:
  - cf_user
  - cf_credit_result
  - cf_loan_application

安全限制:
  - 拦截 Write 操作（INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE）
  - 强制 LIMIT 子句
  - 数据脱敏（电话号码、身份证号）
```

**状态**: 配置正确，但默认指向 `localhost`，Docker 内应设为 `mysql:3306`。

#### 3.3.2 Milvus 向量库

```
连接: MILVUS_HOST (默认 milvus), MILVUS_PORT (默认 19530)
集合: crediflow_knowledge
维度: 可配置（Qwen: 1024, Zhipu: 1024, Ernie: 384, OpenAI: 1536）
索引: IVF_FLAT, L2 距离

流程: 文本 → 嵌入模型 → 向量 → Milvus 存储
检索: 查询 → 嵌入模型 → 向量 → Milvus 搜索 top-3 → 返回内容+来源
```

**状态**: 基础设施就绪，但**需要先调用 `/api/v1/knowledge/ingest` 灌入知识文档**。当前 Milvus 为空。

#### 3.3.3 Java HTTP（NL2API）

```python
API_WHITELIST = {
    "get_active_credit": "/api/app/credit/internal/active",
}
# GET {JAVA_SERVICE_BASE}/api/app/credit/internal/active?userId=xxx
```

**状态**: 白名单只有一个 API。Java 端 `CreditInternalController.getActiveCreditInternal()` 存在。

#### 3.3.4 LLM 适配器

```
llm_adapters.py:
  QwenAdapter   → return f"[Qwen Response] {prompt}"    ← 存根
  ZhipuAdapter  → return f"[Zhipu Response] {prompt}"   ← 存根
  ErnieAdapter  → return f"[Ernie Response] {prompt}"   ← 存根

对比:
  embedding_adapters.py  →  真实 API 调用 ✅
  llm_adapters.py        →  静态字符串存根 ❌
```

**状态**: 所有 LLM 适配器返回的都是 `"[Provider Response] {prompt}"` 而非真实推理结果。向量检索能跑，但生成答案这一步是假的。

### 3.4 ReAct 编排链路（`/api/v1/credit/evaluate`）

```
POST /api/v1/credit/evaluate {userId: 123}
         │
         ▼
  ┌─────────────────────────────────────┐
  │ Step 1: RAG 检索                    │
  │   查询 Milvus → 检索风控政策/规则    │
  │   ✅ 基础设施就绪（但知识库为空）      │
  ├─────────────────────────────────────┤
  │ Step 2: NL2SQL 查询                 │
  │   MySQL → 查历史授信/借款记录        │
  │   ✅ 基础设施就绪                    │
  ├─────────────────────────────────────┤
  │ Step 3: NL2API 外部数据             │
  │   GET Java /api/app/credit/internal/active │
  │   ✅ 接口存在                       │
  ├─────────────────────────────────────┤
  │ Step 4: LLM 裁决                    │
  │   综合 Step1-3 的结果 → 风险评估     │
  │   ❌ LLM 适配器是存根               │
  └─────────────────────────────────────┘
```

---

## 四、Java 端集成全景

### 4.1 Java → Python（Feign 客户端）

**文件**: `credit-risk-service/.../feign/AgentClient.java`

```java
@FeignClient(name = "agent-service", fallback = AgentClientFallback.class)
```

| Feign 方法 | HTTP 路径 | 仅在 agent-python 存在? | 在 data-agent 存在? |
|-----------|-----------|----------------------|---------------------|
| `manualReviewAssistant()` | `POST /manual_review_assistant` | ✅ 是 | ❌ 否 |
| `creditRejectionInsight()` | `POST /credit_rejection_insight` | ✅ 是 | ❌ 否 |

**关键发现**: Java 通过 Feign + Nacos 服务发现调用 `agent-service`，但部署的却是 `data-agent`。路径不匹配，请求必然 404。

### 4.2 降级机制

**AgentClientFallback**: 当 Python 不可用时，返回安全默认值：
```java
defaultProbability: 0.99    // 默认高违约概率
fraudProbability: 0.99     // 默认高欺诈概率
suggestion: "建议拒绝"       // 默认拒贷
```

这意味着 Python 挂了也不会阻塞业务流程，但风控质量降级为"一律拒绝"。

### 4.3 调用方

| Java 类 | 触发时机 | 调用方法 |
|---------|---------|---------|
| `CreditServiceImpl.applyCredit()` | 硬规则引擎拒绝时 | `creditRejectionInsight()` |
| `ManualReviewAsyncService` | 申请进入人工审核时（异步） | `manualReviewAssistant()` |

### 4.4 Python → Java 反向调用

| Python 模块 | 调用目标 | 方式 |
|------------|---------|------|
| agent-python `/chat_intent_risk` | `POST /api/internal/credit/risk-signal/escalate` | 硬编码 `localhost:8080` |
| data-agent NL2API | `GET /api/app/credit/internal/active` | 环境变量 `JAVA_SERVICE_BASE` |

---

## 五、实际问题汇总

```
┌──────────────────────────────────────────────────────────────────┐
│                    Python Agent 双模块问题全景                     │
├────┬─────────────────────────────────────────────────────────────┤
│ #  │ 问题                                                        │
├────┼─────────────────────────────────────────────────────────────┤
│ 1  │ 两模块同一端口(8000)，只能部署一个                           │
│ 2  │ Java 调用的端点 在 agent-python，但部署的容器 是 data-agent │
│ 3  │ agent-python 无 Dockerfile/容器定义，无法被 Nacos 发现      │
│ 4  │ data-agent 的 7 个端点没有被 Java 调用                      │
│ 5  │ data-agent LLM 适配器是存根（返回静态字符串）               │
│ 6  │ Milvus 知识库为空，RAG 检索无结果                           │
│ 7  │ NL2API 白名单只有 1 个 Java API                             │
│ 8  │ batch-service RiskDispatchJob 调 /evaluate 但送空数据       │
│ 9  │ agent-python 反向调用 URL 硬编码 localhost:8080             │
│ 10 │ agent-python 4/7 端点是 Mock 或无人调用                    │
│ 11 │ data-agent 默认 MySQL 地址是 localhost，Docker 内需改      │
│ 12 │ 两模块的功能有重叠 (NL2SQL, RAG) 但实现不兼容              │
└────┴─────────────────────────────────────────────────────────────┘
```

### 优先级排序

```
🔴 阻塞性问题:
  #1 + #2: 部署的服务和 Java 调用的端点不匹配 → Agent 功能完全不可用
  #5: LLM 适配器存根 → data-agent 即使被调用也返回假结果

🟡 功能缺口:
  #3: agent-python 需要容器化或合并到 data-agent
  #6: Milvus 知识库需初始化灌入
  #8: batch-service 数据对接

🟢 优化:
  #7: NL2API 白名单扩展
  #9: 反向调用 URL 改为环境变量/服务发现
  #10: 清理 Mock 端点
```

---

## 六、推荐整合方案

```
现状:
  agent-python (6端点, 无容器)    data-agent (7端点, 有容器)
       │                                │
       ├── 端点被Java调用 ✅              ├── 端点无人调用 ❌
       ├── LLM 真实调用 ✅               ├── LLM 存根 ❌
       ├── 无数据库 ❌                    ├── MySQL + Milvus ✅
       └── 无容器 ❌                      └── 有 Dockerfile ✅

目标 (二合一):
  ┌─────────────────────────────────────────────────┐
  │              data-agent (统一)                   │
  │                                                 │
  │  从 agent-python 迁移的端点:                     │
  │    POST /manual_review_assistant                │
  │    POST /credit_rejection_insight               │
  │                                                 │
  │  data-agent 现有端点:                            │
  │    POST /api/v1/agent/rag                       │
  │    POST /api/v1/agent/nl2sql                    │
  │    POST /api/v1/agent/nl2api                    │
  │    POST /api/v1/credit/evaluate                 │
  │    POST /api/v1/knowledge/ingest                │
  │                                                 │
  │  基础设施:                                       │
  │    MySQL(只读) ✅                                │
  │    Milvus ✅                                     │
  │    Docker ✅                                     │
  │    Nacos 注册 ✅                                 │
  │                                                 │
  │  待修复:                                         │
  │    LLM 适配器 → 真实 API 调用                    │
  │    Milvus → 初始化知识库                         │
  │    agent-python → 删除                           │
  └─────────────────────────────────────────────────┘
```

### 整合后的端点映射

| 原所属 | 端点 | 整合到 | 调用方 |
|--------|------|--------|--------|
| agent-python | `/manual_review_assistant` | data-agent | Java AgentClient |
| agent-python | `/credit_rejection_insight` | data-agent | Java AgentClient |
| data-agent | `/api/v1/agent/rag` | 保留 | (内部/AI 编排) |
| data-agent | `/api/v1/agent/nl2sql` | 保留 | (内部/手动查询) |
| data-agent | `/api/v1/agent/nl2api` | 保留 | (内部/ReAct 链) |
| data-agent | `/api/v1/credit/evaluate` | 保留 | batch-service |
| agent-python | `/nl2sql` (Mock) | **删除** | — |
| agent-python | `/post-loan-warning` | **删除** | — |
| agent-python | `/rag/ask` (Mock) | **删除** | — |
| agent-python | `/chat_intent_risk` | 视需要迁移 | — |
| data-agent | `/api/v1/agent/ocr` (Mock) | **删除** | — |
| data-agent | `/api/v1/agent/face_verify` (Mock) | **删除** | — |
