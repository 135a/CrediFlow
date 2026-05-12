## Why

CrediFlow 需要一份与《智能小额信贷业务系统》提示词一致、可评审的 **需求级规格契约**：把「Java 微服务 + APISIX 网关 + Go 调度 + Python Data Agent + 消息与存储」的边界与合规要求说清楚，便于后续 `design`/`specs`/`tasks` 分阶段落地，并满足校招/工程化展示对 **可解释架构与可追溯行为** 的诉求。当前仓库尚无基线 `openspec/specs/`，因此以本变更为起点建立规格体系。

## What Changes

- 新增本变更下的 **OpenSpec 规格驱动产物链**：本 `proposal` 锁定能力范围；后续将补充 `design.md`、按能力拆分的 `specs/<capability>/spec.md`，以及 `tasks.md`（本次变更范围约定为 **文档与规格**，不强制提交业务实现代码）。
- 明确 **App 端与管理端** 各一个 **BFF 可部署模块**（`app-bff-service` / `admin-bff-service`），经 APISIX 路径分流；领域微服务保持单套实现。
- 数据层 **不引入分库分表**（与 ShardingSphere 等中间件解绑），以单库 MySQL 为权威存储。
- Python Data Agent 按 **真实 LLM 对接** 定义行为与接口契约；**具体厂商/模型/密钥与部署网络边界** 在 `design.md` 中收敛（若仍有未定项，以 `[待确认]` 显式标出，不阻塞规格结构）。

## Capabilities

### New Capabilities

- `gateway-apisix`：统一入口路由、JWT 鉴权、限流熔断、IP 黑白名单、入口审计日志、灰度与安全插件策略；明确不承担业务逻辑；按 **`/api/app/**` 与 `/api/admin/**`** 分流至对应 BFF 上游。
- `bff-app`：App 端 BFF 模块（`app-bff-service`）；面向终端用户 API 聚合与字段裁剪；路由前缀与鉴权角色与后台隔离。
- `bff-admin`：管理端 BFF 模块（`admin-bff-service`）；面向运营/风控/系统管理；与 `system-service` 权限模型对齐；禁止与 App 端共享特权默认配置。
- `microservice-user`：注册登录、认证方式（短信/人脸等）边界、个人信息与画像采集、敏感字段脱敏与存储要求。
- `microservice-credit-risk`：授信评估、规则校验、风险等级、与 Agent 的「建议/辅助」边界及最终授信裁决归属。
- `microservice-loan-application`：申请提交、资料审核、状态机、放款资格校验及与授信/合同的联动约束。
- `microservice-loan-contract`：电子合同生成、签署、归档、查询与合规校验、操作留痕。
- `microservice-repayment`：还款计划、主动还款、分期、金额构成（本金/利息/手续费）与还款方式适配。
- `microservice-post-loan`：逾期判定、罚息、状态更新、催收任务发起及与调度/Agent 的协同边界。
- `microservice-fund-flow`：放款/还款流水、对账校验、统计与第三方支付对接预留。
- `microservice-system-admin`：角色/菜单/权限、后台多角色隔离、操作日志审计。
- `integration-feign-http`：微服务间 **同步 HTTP/OpenFeign** 调用的通用要求（超时、错误语义、不应暴露内部实现）。
- `integration-rocketmq`：异步消息主题/事件类型级约束（解耦、可靠投递、幂等与补偿在业务 spec 中落地）。
- `scheduler-go`：分布式任务清单（代扣、逾期巡检、罚息、提醒、风控异步分发、消息推送调度等）、分片/重试/监控与「不重复、不遗漏」要求。
- `agent-python`：LangGraph 编排、RAG、向量检索、NL2SQL、NL2API、报表与归因等能力的服务边界；与 Java/Go 的 HTTP 契约及「不参与核心事务与权威账本」原则。
- `data-storage`：MySQL **单库** 核心业务、Redis 缓存与分布式锁、RocketMQ、向量库存储角色；敏感数据加密；**本项目不采用分库分表 / ShardingSphere**。
- `deployment-compose`：Docker Compose 一键编排目标、服务依赖顺序与环境变量分组（不含具体 compose 文件实现时，仍以 **部署要求** 形式出现）。
- `observability-platform`：Prometheus/Grafana、链路追踪等 **预留集成点** 与最低可观测性要求（指标/日志/追踪字段边界）。

### Modified Capabilities

- 无：当前 `openspec/specs/` 下尚不存在基线能力规格；后续若从本变更归档到主规格，再对主规格使用 delta 流程。

## Impact

- **仓库**：主要影响 `openspec/changes/crediflow-micro-credit-system/` 下后续 `design.md`、`specs/**`、`tasks.md` 的结构与工作量评估。
- **系统与依赖**：规格层将引用 Nacos、Sentinel、MyBatis-Plus、Spring Security+JWT、MySQL、Redis、RocketMQ、APISIX、Go 运行时、Python Agent 运行时、LLM 与向量库等外部组件的责任边界；**LLM 供应商与向量库选型** 为关键外部依赖，默认在设计与任务阶段给出可选方案与推荐默认值，最终以你的确认项为准。
- **合规表述**：以「可审计、可追溯、幂等、数据最小化与脱敏」为硬性要求；若需对齐特定司法辖区或行业条文，将在 `[待确认]` 项中补齐引用与映射矩阵。
