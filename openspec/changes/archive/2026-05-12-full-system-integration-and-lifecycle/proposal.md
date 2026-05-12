## Why

目前 CrediFlow 系统仅完成了“用户准入 -> KYC 认证 -> 授信风控评估 -> 借款申请”的前半段链路。为了支撑完整的企业级小额信贷业务闭环，并真正落地架构蓝图中的核心亮点（Go 分布式任务调度与 Python Agent 深度应用），我们必须一揽子解决贷中、贷后、资金结算以及智能化等全域链路的空白。只有补全这部分功能，项目才能具备真实的生产级可用性，并足以支撑高含金量的校招或社招简历展示。

## What Changes

本变更是针对整个系统核心架构与全生命周期的全覆盖升级：
- **借款下半场生命周期落地**：
  - 实现 `loan-contract-service`，在借款申请通过后，生成具有法律效力的电子合同。
  - 实现 `repayment-service`，在放款成功后，为用户生成分期还款计划（本金+利息+手续费），并提供主动还款网关。
  - 完善 `fund-flow-service`，对接支付宝/微信等第三方 Mock 支付网关，记录真实出入金流水。
- **贷后与 Go 分布式调度打通**：
  - 实现 `post-loan-service`，提供逾期状态转移与罚息计算的内部接口。
  - 落地 `scheduler-go` 服务，利用 Go 并发优势，编写定时器去定时触发“自动代扣还款”、“每日逾期状态巡检”和“罚息递增计算”。
- **AI 智能化深度整合**：
  - 完善 `agent-python`，接入真实业务数据库表结构（通过元数据注入）以实现真实的 **NL2SQL**（运营自然语言查数）。
  - 增加大模型的 **贷后预警** 能力：每日汇总逾期数据交由 Agent 分析并输出坏账预警报告。
  - 落地 **RAG 金融知识库客服**：结合 Milvus 向量库，回答 C 端关于利率、逾期规则的提问。
- **基础设施与权限闭环**：
  - 完善 `gateway-apisix` 配置逻辑，真实拦截无权限请求并进行 JWT 验签。
  - 落地 `microservice-system-admin` 的基础 RBAC 模型，确保风控人员和运营人员权限隔离。

## Capabilities

### New Capabilities
- `nl2sql-data-analysis`: 基于大模型的自然语言转 SQL 及业务报表生成能力。
- `ai-post-loan-warning`: 贷后大模型坏账风险预警与归因能力。
- `rag-customer-service`: 基于向量检索增强的智能客服问答能力。

### Modified Capabilities
- `microservice-loan-application`: 借款通过后，触发合同生成与放款事件（**BREAKING**: 增加异步事件抛出流程）。
- `microservice-loan-contract`: 从 TBD 补充具体的合同生成与签署业务规则。
- `microservice-repayment`: 从 TBD 补充还款计划生成、还款金额核算及主动/被动还款规则。
- `microservice-post-loan`: 从 TBD 补充逾期判定、状态降级及罚息计算规则。
- `microservice-fund-flow`: 补充真实第三方资金划扣对接及流水账单核对规则。
- `microservice-system-admin`: 补充基于角色的权限校验规则。
- `scheduler-go`: 补充具体的三个定时任务（代扣、巡检、罚息）执行频率及重试重试。
- `gateway-apisix`: 补充全局路由转发及 JWT 鉴权规则。

## Impact

这是一次牵一发而动全身的核心升级。
- **跨库事务挑战**：借款成功后涉及合同生成、资金流水落库、还款计划生成的分布式流程，需依赖 RocketMQ 实现最终一致性（或使用分布式事务中间件）。
- **语言壁垒跨越**：Go 调度系统将通过 HTTP 批量异步调用 Java 接口；Python Agent 将利用 MySQL 连接池直接嗅探只读业务表进行 NL2SQL 转换。
- **架构升级**：系统复杂度将达到中型互联网公司准生产级别。
