## 1. 仓库基线与文档

- [x] 1.1 建立多模块仓库骨架（Java 父工程、Go 调度模块目录、Python Agent 目录）并与本变更 `design.md` 目录命名对齐
- [x] 1.2 编写根 `README.md`：架构图引用位、技术栈、本地启动前置条件、与合规声明（不对牌照作结论）
- [x] 1.3 编写「简历版」项目描述（三语言协同、微服务 + Agent、个人可写职责占位）
- [x] 1.4 新增 `admin-bff-service`（管理端 BFF，`/api/admin/**`）与 `app-bff-service`（App 端 BFF，`/api/app/**`）两个可部署 Maven 模块；网关分流说明见 `infra/apisix/ROUTES-APP-ADMIN.md`
- [x] 1.5 规格与设计明确「单库 MySQL、不采用分库分表 / 不引入 ShardingSphere」并更新 `data-storage` 与 `docs/sharding-sphere.md`

## 2. Docker Compose 与网络

- [x] 2.1 新增 `docker-compose`：划分 `edge` / `internal` 网络，MySQL、Redis、RocketMQ、Nacos、Milvus 仅挂 internal
- [x] 2.2 为各服务添加 `depends_on` 与健康检查，保证启动顺序符合 `deployment-compose` spec
- [x] 2.3 提供 `.env.example` 列出必填环境变量（DB、MQ、JWT、各 LLM Key、`ACTIVE_PROVIDER` 等），禁止提交真实密钥

## 3. 数据存储与迁移

- [x] 3.1 定义 MySQL 初始化脚本或迁移工具（Flyway/Liquibase 二选一并固化），覆盖用户、授信、申请、合同、借据、还款计划、流水、审计表最小集
- [x] 3.2 实现敏感字段加解密工具类与密钥缺失时启动失败逻辑（对齐 `data-storage` spec）
- [x] 3.3 配置 Redis 连接与分布式锁封装（TTL、value 校验），供还款/放款幂等使用（当前在 `repayment-service`，避免全模块强依赖 Redis；后续可提取至 `crediflow-common`）
- [x] 3.4 ~~评估并文档化 ShardingSphere 启用阈值~~ → **已决策不采用分库分表**；保留单库容量与索引优化指引（见 `docs/sharding-sphere.md`）

## 4. RocketMQ 与事件契约

- [x] 4.1 定义主题命名、事件体通用字段（`eventId`、`occurredAt`、`schemaVersion`）及版本协商失败处理
- [x] 4.2 实现生产者「事务后发送」或事务消息模式（对齐 `integration-rocketmq` spec）
- [x] 4.3 实现消费者幂等存储（去重表或 Redis）与死信队列策略（表：`cf_mq_consumer_processed`；DLQ 策略见 `docs/rocketmq-dlq.md`）

## 5. Java 公共横切

- [x] 5.1 接入 Nacos 注册与配置；统一应用名、命名空间、配置分组约定
- [x] 5.2 接入 Sentinel：资源名规则、与网关限流分层说明文档 + 最小演示规则
- [x] 5.3 实现全局 `X-Request-Id` / trace 透传过滤器与 Feign 拦截器（对齐 `integration-feign-http` 与 `observability-platform`）
- [x] 5.4 固化内网互信方案（JWT 或 mTLS 二选一）并在代码与文档中落地一种
- [x] 5.5 统一异常体与错误码，禁止向前端返回堆栈；暴露 `/metrics` 端点（Prometheus）

## 6. Apache APISIX

- [x] 6.1 编写路由配置：按路径前缀转发至各微服务上游；区分用户端与管理端路由
- [x] 6.2 配置 JWT 校验插件与 401 行为；配置 request-id 注入
- [x] 6.3 配置限流（IP/用户维度）与 IP 黑白名单示例
- [x] 6.4 启用入口访问日志（结构化字段对齐 gateway-apisix spec）与基础 WAF/SQLi 防护插件

## 7. 微服务：用户与权限

- [x] 7.1 实现 `user-service`：注册、登录、令牌签发、登录审计；敏感字段存储加密与查询脱敏
- [x] 7.2 为短信/人脸认证预留接口与开关（未开通时明确错误语义）
- [x] 7.3 实现 `system-service`：角色、权限、菜单绑定与后台接口鉴权；操作审计日志查询

## 8. 微服务：授信、申请与合同

- [x] 8.1 实现 `credit-risk-service`：规则引擎骨架、授信结果持久化、Agent 可选调用与超时降级
- [x] 8.2 实现 `loan-application-service`：申请单状态机、幂等创建、与授信/合同状态联合校验
- [x] 8.3 实现 `loan-contract-service`：最简合规签署（前端勾选同意）、协议 PDF 生成与链接查询、后台存证（PDF + 日志 + 流水）

## 9. 微服务：还款、贷后与资金

- [x] 9.1 实现 `repayment-service`：还款计划生成、主动还款、幂等还款接口、还款方式配置开关
- [x] 9.2 实现 `post-loan-service`：逾期判定、罚息计算明细、催收任务记录与 MQ/调度触发点
- [x] 9.3 实现 `fund-flow-service`：放款/还款流水入账、对账汇总任务、第三方支付回调验签占位

## 10. Go 分布式调度

- [x] 10.1 搭建调度进程框架：配置化 cron、任务开关、分片与重试指标日志
- [x] 10.2 实现代扣任务：调用还款服务 HTTPS 接口，携带幂等键
- [x] 10.3 实现逾期巡检、罚息计算、还款提醒、风控异步投递、消息推送调度任务（可先部分为 no-op + 日志）
- [x] 10.4 禁止调度器直连 MySQL 写业务库；只读查询若需要则走只读账号与文档说明

## 11. Python Data Agent

- [x] 11.1 实现 LLM 抽象接口与通义/智谱/文心三套适配器及 `ACTIVE_PROVIDER` 单活配置
- [x] 11.2 接入 Milvus：集合设计、embedding 写入管道、删除/更新策略文档
- [x] 11.3 实现 LangGraph 最小编排：RAG 问答（带 `source_id`）、模型/提示词版本审计字段
- [x] 11.4 实现 NL2SQL：只读连接、表白名单、静态分析拦截写语句、行数上限与列掩码
- [x] 11.5 实现 NL2API：API 白名单与 JSON Schema 参数校验；高危写接口默认不在白名单
- [x] 11.6 提供 Agent 与 Java 服务间 HTTPS 调用示例与超时/熔断配置

## 12. 可观测性与联调

- [x] 12.1 添加 Prometheus 抓取配置样例与最小 Grafana 仪表盘 JSON（可选）
- [x] 12.2 接入结构化 JSON 日志与 `request_id`/`trace_id` 字段校验用例
- [x] 12.3 预留 OpenTelemetry 无 collector 降级路径并文档化
- [x] 12.4 端到端联调清单：注册→授信→申请→合同→放款消息→计划→还款→流水对账（勾选式回归表）

## 13. 规格符合性校验

- [x] 13.1 按 `openspec/changes/crediflow-micro-credit-system/specs/**/spec.md` 逐条建立需求-测试映射表（或 BDD 特性文件占位）
- [x] 13.2 对幂等、审计、网关无业务逻辑等高风险项编写集成测试或手工验收脚本
