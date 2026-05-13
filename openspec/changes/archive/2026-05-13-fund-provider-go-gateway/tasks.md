# fund-provider-go-gateway — 实现任务清单

> 阶段 0（第 1 批）已交付：Go 网关骨架 + Nacos 配置与启动强校验 + 对内 API 与 DTO 契约
> + Mock Provider 与加签/加解密 hook 占位 + Redis 幂等 + 回调入口 + RocketMQ 桥接默认即 B。
> 实现位置：`go/fund-channel-gateway/`。详情见 `go/fund-channel-gateway/README.md`、
> `go/fund-channel-gateway/docs/api-contract.md`、`go/fund-channel-gateway/docs/nacos-config.md`。
> 第 1 批锁定的 6 项决策：(1) 桥接=方案 B/RocketMQ；(2) 路由=混合模式（Java 传 providerId 优先，缺省 Nacos 默认）；
> (3) 绑卡=token 化 `bindCardId`；(4) 网关持久化=Redis SETNX；(5) 框架=Gin + sentinel-golang；
> (6) 范围=只做骨架与 Mock，不动 Java、不删旧代码。
>
> 阶段 2（第 2 批）已交付：`fund-flow-service` 切到经 Go 网关受理，引入 `crediflow.fund.use-gateway`
> 灰度开关与 `payload_digest` 等流水扩展列。
>
> 阶段 3（第 3 批）已交付：6.3 + 8.x + 9.x + 10.x。Java 端新增 `crediflow-common`
> 的 `FundDisbursedTerminalEvent` / `RepaymentSettledEvent` 桥接事件 DTO 与
> `TOPIC_FUND_DISBURSED_TERMINAL` / `TOPIC_REPAYMENT_SETTLED` 常量；
> `fund-flow-service`、`repayment-service`、`post-loan-service` 各自订阅相应终态 Topic。
> 主动还款（`/api/app/repayment/pay`）改走 `RepaymentService#activeRepay` -> Go 网关
> `/internal/v1/repay`，期次进入 `SUBMITTED` 中间态，终态由 MQ 桥接核销；旧 mock
> 收银网关 `processRepayment` 已禁用并抛禁用异常。`batch-service` 每日代扣改为
> 调用 `/api/internal/repayment/due-today` 拉取期次后并发请求网关 `/internal/v1/withhold`，
> 输出 accepted / dup / rejected / circuit / transport 指标，便于监控告警。

## 1. Go 资金网关骨架与运维面

- [x] 1.1 在 `go/fund-channel-gateway` 初始化 Go module、入口 `main`、HTTP 框架（Gin/Echo 择一并与现有 `batch-service` 风格对齐）、`/health` 与 `/ready` 探针。
- [x] 1.2 增加 `Dockerfile` 与根目录 `docker-compose` 片段（或现有 compose 中的 service 块），使网关可与 `batch-service` 并行构建与启动。
- [x] 1.3 接入结构化日志、OpenTelemetry trace（与 `X-Trace-Id` 透传约定一致），并确保日志中不输出完整卡号、证件与密钥。

## 2. Nacos 配置与启动强校验

- [x] 2.1 定义并文档化 Nacos DataId/Group 约定（如 `fund-provider.yaml` 或 `fund-provider-{providerId}.yaml`），字段覆盖 `design.md` D2：`baseUrl`、`appKey`、`appSecret`、签名算法、加密密钥引用、`httpTimeoutMs`、`maxRetries`、熔断阈值、回调白名单等。
- [x] 2.2 网关在启动时校验：任一 `enabled=true` 的 `providerId` 若缺少必填项则 **拒绝启动**；生产环境禁止 `mockSuccess`（若引入 Mock）类开关误开（对齐设计中的强断言思路）。
- [x] 2.3 在仓库内提供 **不含生产密钥** 的 `fund-provider` 配置示例（仅 dev/test 占位），并在 README 或变更目录下注明运维注入方式。

## 3. 对内 REST API（Java → Go）

- [x] 3.1 实现 `POST /internal/v1/disburse`、`POST /internal/v1/repay`、`POST /internal/v1/withhold`（路径与命名可按设计最终统一），请求/响应 DTO 与 OpenAPI 或 Markdown 契约文档同步到 `java/` 侧 Feign 生成或手写参考。
- [x] 3.2 实现内网中间件：校验 `X-Internal-Sign`、时间戳防重放（与 `crediflow-common` 或现有 Java 拦截器算法对齐）；非法请求返回 401。
- [x] 3.3 同步响应仅返回「受理态」（如 `ACCEPTED` + `gatewayRequestId`），错误码区分配置缺失、熔断、参数非法；终态不由此接口冒充。

## 4. 资金方出站调用占位与韧性

- [x] 4.1 实现可插拔 `FundProviderClient` 接口：输入业务 DTO，输出同步层结果（受理/明确失败/可重试）；默认提供 **Mock 实现**（测试环境固定延迟 + 可选触发异步回调）。
- [x] 4.2 集成 HTTPS 客户端、连接/读超时、`context` 取消；对可重试错误实施有限次退避重试；集成熔断器（如 `sentinel-golang` 或等价）并在打开时快速失败。
- [x] 4.3 预留请求加签、响应验签、AES/RSA 字段加解密钩子（具体算法实现可 TODO，但 MUST 有稳定扩展点与单元测试桩）。

## 5. 幂等与审计存储

- [x] 5.1 为网关选型持久化（PostgreSQL/MySQL/Redis 之一，与项目基础设施一致）：保存 `(providerId, businessKey, operation)` 唯一约束或等价 SETNX，支撑重复受理与重复回调短路。
- [x] 5.2 记录每笔外呼与回调：报文 SHA-256 摘要、`gatewayRequestId`、HTTP 状态、验签结果、熔断状态；禁止写入明文敏感字段。

## 6. 异步回调与 Java 桥接

- [x] 6.1 实现对外 `POST /fund/callback/{providerId}`（或设计最终路径）：读取原始 body → 验签 → 幂等判定 → 解析终态。
- [x] 6.2 **锁定 Open Question**：实现主桥接路径（二选一或组合）：**(b)** 向 RocketMQ 投递 `FUND_DISBURSED_EVENT` / `REPAYMENT_SETTLED_EVENT`（推荐）；和/或 **(a)** 带 `X-Internal-Sign` 调用 Java 内部回调 URL；在代码与配置中只保留一种默认，其余以 feature flag 关闭。
- [x] 6.3 在 `fund-flow-service`、`repayment-service` 中调整或新增消费者/Controller，使终态来源统一为网关桥接事件，并删除对「资金方直连回调」的假设（若有残留）。

## 7. Java — `fund-flow-service`

- [x] 7.1 新增 Feign 客户端（或 RestClient）指向 `fund-channel-gateway`，请求拦截器附加内网签名与 `X-Trace-Id`。
- [x] 7.2 将 `CONTRACT_READY_EVENT` 消费链中「调用第三方/Mock 放款」替换为「调用 Go 放款受理接口」；保留防重与事务边界；增加 feature flag 以便灰度回滚。
- [x] 7.3 移除或禁用 JVM 内直连资金方的 HTTP 客户端与配置项（`AppSecret` 等不得再出现在 Java 配置）。
- [x] 7.4 数据库迁移：为资金流水表增加 `provider_id`、资金方流水号/网关请求号、回调摘要引用等字段（与 delta spec 一致），并更新实体与 Mapper。

## 8. Java — `repayment-service`

- [x] 8.1 主动还款路径：在获取 `idmpToken` Redis 锁成功后，改为调用 Go `repay/withhold` 受理接口；终态依赖事件或回调桥接后再核销计划。
- [x] 8.2 删除或禁用 Java 内「第三方收银网关」直连实现与相关配置。
- [x] 8.3 补充集成测试：幂等 Token 重复点击、受理成功但回调延迟时的订单状态（至少文档化中间态查询策略）。

## 9. Go — `batch-service` 代扣调度

- [x] 9.1 修改「每日自动代扣」Job：由调用 Java `repayment-service` 内部扣款接口改为调用 `fund-channel-gateway` 的 `withhold` 受理接口（内网签名 + 业务单号维度与主动还款对齐）。
- [x] 9.2 保留 Redis 分布式锁与批处理并发模型；为调用网关增加超时、错误计数与告警指标。

## 10. Java — `post-loan-service` 与画像

- [x] 10.1 订阅还款结清/放款失败等桥接事件，按 delta spec 幂等更新履约相关数据与 outbound 通道（调用用户画像/风控服务的接口占位需与现有模块对齐）。
- [x] 10.2 确保放款失败不得错误进入「还款中」状态（与借款/合同状态机联调）。

## 11. 联调、灰度与验证

- [ ] 11.1 使用 Mock 资金方完成端到端：放款受理 → 异步回调 → MQ → Java 更新订单/流水；还款与定时代扣两条链路同理。
- [ ] 11.2 压测网关回调入口与内网受理 QPS，验证熔断与限流参数默认值。
- [ ] 11.3 运行 `openspec validate fund-provider-go-gateway`（或项目约定的校验命令）确保 change 包通过校验，并在 PR 描述中列出 **BREAKING** 与回滚开关位置。
