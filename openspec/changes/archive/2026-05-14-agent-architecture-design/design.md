## Context
CrediFlow 系统目前正处于从早期粗粒度微服务向“智能体（Agent）”架构演进的阶段。为了解决风控引擎、动态定价等计算/规则密集型逻辑与日常的 CRUD 业务流转耦合过深的问题，系统架构基于《信贷项目智能体设计与分工文档.md》进行了全面升级，将信贷核心生命周期解耦为 5 个领域智能体。

## Goals / Non-Goals

**Goals:**
- 将系统清晰划分为 5 大 Agent：用户服务 (User Service)、反欺诈与风控 (Anti-Fraud & Risk)、额度定价 (Credit Limit & Pricing)、合约流程 (Contract Process)、贷后管理 (Post-Loan)。
- 确立 Java (侧重业务流转与强事务) 与 Python (侧重数据分析与规则引擎) 的异构技术栈分工边界。
- 定义以 Go APISIX 为核心网关的跨 Agent 通信链路架构。

**Non-Goals:**
- **暂不涉及**引入真实的复杂 AI 深度学习模型，重点在于搭建 Agent“壳”与规则出入参。
- **暂不涉及**底层数据库表的重大重构，现有微服务底座直接平滑升格为对应的 Agent。

## Decisions

**Decision 1: 异构技术栈的精准切分**
- **方案**：
  - **Java (Spring Cloud)**：承载 用户服务、合约流程、贷后管理 Agent。
  - **Python (Flask/Django)**：承载 反欺诈与风控、额度定价 Agent。
- **Rationale**：Java 具备企业级微服务的成熟度，在事务控制、并发处理方面极为稳健，适合做流程控制和资金账务管理。Python 凭借 pandas/numpy/scikit-learn 及灵活的轻量级规则引擎包，能够让风控分析师与数据团队快速迭代策略，而不用受限于 Java 冗长的发布周期。

**Decision 2: 通信链路与网关解耦**
- **方案**：Agent 之间尽量避免直连点对点 RPC。前端流量和 Agent 跨域流量统一经由 Go APISIX 网关处理（鉴权、限流、路由）。例如，用户服务 Agent 提交进件后，通过内部 REST/MQ 接口通知 Python 风控 Agent 进行审批。
- **Rationale**：网关中心化能有效应对多语言跨栈调用时的链路追踪和权限控制，保障风控、定价核心数据接口的安全。

## Risks / Trade-offs

**Risks:**
- **[异构运维复杂度]**：Java 和 Python 混合编排，开发人员在联调时需要同时启动两套环境。
  - **Mitigation**：利用 Docker 及 `docker-compose` 提供一键式的本地联调容器网络，隐藏跨语言的运行环境差异。
- **[分布式一致性挑战]**：跨 Agent 的异步流转（如风控审批通过后，触发额度计算与合同生成）如果中断，容易出现脏状态。
  - **Mitigation**：采用最终一致性设计。每个核心状态跃迁（进件 -> 审批 -> 授信 -> 签约）都在发起方 Agent 的数据库中维护明确的状态机，依赖定时任务补偿机制或者 MQ 可靠消息机制，放弃使用重的两阶段提交（2PC/XA）。
