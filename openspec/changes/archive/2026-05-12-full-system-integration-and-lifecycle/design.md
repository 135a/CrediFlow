## Context

CrediFlow 系统初期仅跑通了“用户准入与风控授信”的前半段，为了完成整个小额信贷业务的全生命周期并落实多语言架构（Java+Go+Python）的深度集成，我们需要实施一套复杂的跨服务流转方案。涉及从借款通过后的合同生成、放款、还款计划生成，到 Go 调度器的定时逾期巡检，再到 Python Agent 的数据嗅探与智能预警。

## Goals / Non-Goals

**Goals:**
- **解耦核心业务流**：借款通过后，不再使用同步阻塞的 Feign 调用，而是通过 RocketMQ 消息队列实现后续（合同生成、放款、还款计划生成）的异步解耦。
- **打通多语言边界**：明确 Go 与 Java，Python 与 Java 之间的通讯协议与数据边界。
- **完善全局安全**：由 APISIX 承担全局鉴权，Java 后端剥离鉴权逻辑，仅信赖网关传递的 Header。

**Non-Goals:**
- 不引入重型分布式事务框架（如 Seata 的 AT/TCC 模式），采用“基于本地消息表+MQ”的最终一致性方案。
- Python Agent 在进行 NL2SQL 时不具有写权限，严格限制其通过只读账号直接访问 MySQL，避免数据被篡改。

## Decisions

1. **借款后链路异步事件驱动架构 (Event-Driven)**
   - 决策：借款申请 (`loan-application-service`) 终审通过后，不直接同步调用资金和合同服务，而是向 RocketMQ 投递一条 `LOAN_APPROVED_EVENT` 消息。
   - `loan-contract-service` 监听到消息，生成合同，完成后投递 `CONTRACT_READY_EVENT`。
   - `fund-flow-service` 监听到合同就绪后，调用第三方 API（Mock）放款，放款成功后投递 `FUND_DISBURSED_EVENT`。
   - `repayment-service` 监听到放款成功后，为用户生成分期的还款计划表。
   - 理由：高内聚低耦合，任何一个下游服务（如生成合同的 PDF 渲染慢、或资金系统网络抖动）宕机，都不会阻塞前端用户的借款申请流程，保证核心交易高可用。

2. **Go 调度器跨语言通讯策略**
   - 决策：`scheduler-go` 作为独立的微服务，不直连 MySQL 数据库，而是通过定时器向对应的 Java 微服务（如 `post-loan-service`, `repayment-service`）发送 HTTP POST 触发请求。
   - 理由：保证领域模型只被对应的 Java 服务持有，防止 Go 和 Java 两套代码同时写同一张表造成的并发数据污染。

3. **Python Agent NL2SQL 接入方案**
   - 决策：为 Python Agent 提供独立的只读 MySQL 账号凭证，并允许其读取 `information_schema` 获取表结构。Agent 生成的 SQL 只能在内部沙盒中以 SELECT 权限执行。
   - 理由：实现报表自由查询的同时，死守数据安全底线，防止大模型幻觉导致 `DROP TABLE` 或数据泄露。

4. **APISIX 鉴权与身份透传**
   - 决策：所有 C 端与 Admin 端的请求入口均由 APISIX 接管。APISIX 插件统一校验 JWT，校验成功后，将 `userId` 解析出并放入 `X-User-Id` Header 中转发给下游 Java 服务。
   - 理由：统一安全入口，下层 Java 微服务无需再重复解析 Token，做到“无状态”开发。

## Risks / Trade-offs

- **[Risk] MQ 消息丢失导致链路中断（如放了款但没生成还款计划）**
  **[Mitigation]** 各微服务必须实现消息消费的幂等性设计，同时利用 RocketMQ 的可靠消费机制和重试队列。
- **[Risk] Python 幻觉生成极度耗性能的 SQL 拖垮数据库**
  **[Mitigation]** 执行 Agent 生成的 SQL 时，限制 `LIMIT 100`，并在从库（或限制了最大执行时间的 Session）上执行，防止慢查询拖垮主库。

## Migration Plan
1. 优先部署 RocketMQ 并修改微服务的 Docker Compose。
2. 将现有的所有 Feign 同步调用评估是否需改造为异步 MQ。
3. 按照“事件流”的顺序，从前往后逐个实现（合同 -> 放款 -> 还款 -> 调度）。
