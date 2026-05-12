## 1. 基础消息队列与事件封装

- [x] 1.1 在公共模块 `crediflow-common` 中定义全生命周期 MQ 事件（`LOAN_APPROVED_EVENT`, `CONTRACT_READY_EVENT`, `FUND_DISBURSED_EVENT`）的数据结构常量。
- [x] 1.2 在各相关微服务（借款、合同、资金、还款）的 `pom.xml` 中引入 `rocketmq-spring-boot-starter`。

## 2. 借款终审改造 (loan-application-service)

- [x] 2.1 修改借款审批通过逻辑：取消阻塞式调用，审批通过后将记录状态置为“合同生成中”，并向 MQ 发送 `LOAN_APPROVED_EVENT`。

## 3. 合同系统落地 (loan-contract-service)

- [x] 3.1 创建 `cf_loan_contract` 数据库表及其对应的实体类、Mapper、Service。
- [x] 3.2 编写 MQ 消费者监听 `LOAN_APPROVED_EVENT`，利用固定模板生成合同号并落库，完成后抛出 `CONTRACT_READY_EVENT`。

## 4. 资金放款对接 (fund-flow-service)

- [x] 4.1 编写 MQ 消费者监听 `CONTRACT_READY_EVENT`，接收到消息后调用模拟的第三方支付接口出金。
- [x] 4.2 放款成功后落库出金流水记录表，并向 MQ 抛出 `FUND_DISBURSED_EVENT`，同时调用借款服务接口将借款单状态置为“还款中”。

## 5. 还款系统落地 (repayment-service)

- [x] 5.1 创建 `cf_repayment_plan`（还款计划表）的实体、Mapper 与 Service。
- [x] 5.2 编写 MQ 消费者监听 `FUND_DISBURSED_EVENT`，利用等额本息/本金算法，为这笔借款生成未来N期的还款明细并落库。
- [x] 5.3 暴露主动还款的 Gateway API，允许用户对指定的某期还款计划发起还款请求（Mock 收银），并更新状态为“已结清”。

## 6. 贷后与 Go 调度打通 (post-loan-service & scheduler-go)

- [x] 6.1 在 `post-loan-service` 暴露供内部触发的逾期计算接口，查询未结清且已过期的计划，将其状态置为“逾期”并增加每日罚息。
- [x] 6.2 在 `scheduler-go` 中编写 Cron 任务，每天凌晨准点通过 HTTP 请求触发 Java 端的逾期计算接口。
- [x] 6.3 在 `scheduler-go` 中编写自动代扣还款 Cron 任务，定时触发 `repayment-service` 的批量扣款逻辑。

## 7. Python Agent 智能化补全

- [x] 7.1 **NL2SQL**：为 Agent 配置业务数据库的只读账号并打通 `information_schema` 检索逻辑，让 Agent 能够将运营语言转为真实的统计 SQL 并返回数据报表。
- [x] 7.2 **贷后预警**：编写 Python 脚本定时向 `post-loan-service` 拉取逾期数据，交由 LLM 进行坏账风险因子评估并生成 Markdown 预警报告。
- [x] 7.3 **RAG 问答**：整合 Milvus 与 langchain，注入基本的借款/逾期规则纯文本，跑通 C 端用户的自然语言知识库问答。

## 8. APISIX 网关与基础权限闭环

- [x] 8.1 在 APISIX 控制台（或声明式 yaml 中）配置全局 JWT 认证插件，拦截未授权请求。
- [x] 8.2 配置 APISIX 在 JWT 验签通过后，解包 `userId` 并将其作为 `X-User-Id` Header 附加在请求头上透传给下层微服务。
- [x] 8.3 在 `system-admin-service` 中实现基于 RBAC 模型（角色-权限）的基础校验逻辑，限制普通运营人员越权操作“人工风控强行过审”的敏感接口。
