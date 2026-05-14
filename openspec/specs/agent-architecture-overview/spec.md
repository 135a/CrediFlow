# agent-architecture-overview

## Purpose
明确系统 5 大核心 Agent 的领域边界、通信约束以及跨界流转规范。

## ADDED Requirements

### Requirement: 核心智能体划分与业务隔离
系统 MUST 按照《信贷项目智能体设计与分工文档》的标准，切分为 5 个独立智能体（User Service Agent, Anti-Fraud & Risk Agent, Credit Limit & Pricing Agent, Contract Process Agent, Post-Loan Management Agent）。任何信贷业务逻辑 MUST 明确归属至单一智能体中，各智能体数据隔离，避免跨库直连查询。

#### Scenario: 用户首次进件业务路由
- **WHEN** 用户通过移动端或 Web 端提交借款申请并录入个人资质
- **THEN** 该请求 MUST 仅由“用户服务智能体”处理并持久化，系统 MUST 为其生成全局统一借款申请单号

#### Scenario: 核心流程协同流转
- **WHEN** “用户服务智能体”完成进件初筛
- **THEN** 系统 MUST 依次流转至“风控智能体”进行准入审批，随后流转至“额度定价智能体”进行千人千率计算，最终流转至“合约流程智能体”生成待签合同

### Requirement: 跨技术栈通信网关约束
由于系统采用 Java 与 Python 异构栈，所有跨智能体的通信（如 Java 服务请求 Python 风控引擎）MUST 经由统一的网关（如 APISIX）或者消息队列进行路由与权限校验，MUST NOT 进行服务间的直接地址硬编码 RPC。

#### Scenario: 跨语言调用风控引擎
- **WHEN** Java 的某个 Agent 需要验证当前用户的多头借贷风险状态
- **THEN** 该 Agent MUST 发送包含 TraceID 和 Token 的 HTTP 请求至 Go API 网关，由网关路由至 Python 风控引擎处理并返回统一格式的数据

### Requirement: 基于最终一致性的状态机流转
跨智能体的长流程事务（如放款成功后生成还款计划）MUST 摒弃强一致性的分布式事务（如两阶段提交），转而采用基于本地状态机和消息机制的“最终一致性”设计，允许合理的秒级延迟。

#### Scenario: 签约后异步生成借据
- **WHEN** 用户在“合约流程智能体”中完成电子签署，合同状态跃迁为 SIGNED
- **THEN** “贷后管理智能体” MUST 最终获取到该事件（通过轮询补偿或 MQ 订阅）并生成还款账单，若网络抖动丢失，系统 MUST 能够自动补偿重试而不阻断其他流程
