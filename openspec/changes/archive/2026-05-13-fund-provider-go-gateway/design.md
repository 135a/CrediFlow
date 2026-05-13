## Context

CrediFlow 采用 Java 微服务（`fund-flow-service`、`repayment-service`、`loan-application-service`、`post-loan-service` 等）承载业务与账务，Go 侧已有 `go/batch-service`（定时代扣等批任务）。当前规格与历史实现倾向由 Java 直连「第三方/支付」完成出金与收银，与本次产品约束不一致：持牌资金方为唯一出资与收款主体，银联/银行代扣仅为通道；资金不得经过平台服务器或平台账户。

本设计将「资金方 HTTP API 的鉴权、加解密、重试熔断、异步回调验签」全部收敛到新建的 Go 服务 **`fund-channel-gateway`**（与 `go/batch-service` 并列的独立进程），Java 仅通过内网调用向网关下发业务参数并消费网关桥接回来的结果事件；资金方连接参数与密钥仅从 **Nacos** 拉取，禁止硬编码。

## Goals / Non-Goals

**Goals:**

- 建立 Go 资金网关的进程边界、模块划分与对 Java / 对资金方的双向契约（同步 RPC + 异步回调 + 可选 MQ 桥接）。
- 定义 Nacos 配置模型（多环境、多资金方、热更新策略与安全边界）。
- 明确放款与还款两条主链路的时序、幂等键落点、以及「资金方对公 ↔ 用户储蓄卡」的账务语义在网关与 Java 之间的分工。
- 将 `batch-service` 的自动代扣调度从「调 Java 内部扣款」改为「调 Go 网关代扣接口」，与主动还款共用同一资金出口。
- 约定 Java↔Go 与 Go→Java 回调均满足 `internal-api-security`（内网签名、防重放）；资金类载荷字段最小化与脱敏。

**Non-Goals:**

- 具体某家资金厂商的报文字段映射、签名算法细节（留待对接任务与厂商文档）。
- 前端改造、Python 风控模型与评分逻辑变更。
- 替换或下线 APISIX 的全局路由策略（仅涉及内网服务发现与网关暴露面）。

## Decisions

### D1：新增独立进程 `fund-channel-gateway`

- **内容**：在 `go/fund-channel-gateway` 新增可单独部署的 HTTP 服务（建议 Gin 或 Echo + 标准 `context` 超时），负责：读 Nacos、组装资金方 JSON、客户端加签/敏感字段加密、HTTP(S) 调用、服务端响应验签/解密、熔断与有限次重试、接收资金方异步回调。
- **理由**：与 `batch-service` 解耦，避免批任务进程与长连接/高并发回调争用资源；网关可独立扩缩容与设限流。
- **备选**：在 `batch-service` 内嵌网关模块。**未采纳**：职责混杂，回调与健康检查生命周期不同。

### D2：Nacos 配置结构与密钥管理

- **内容**：使用统一前缀（如 `fund-provider.yaml` 或按资金方 `fund-provider-{providerId}.yaml`），字段至少包含：`baseUrl`、`appKey`、`appSecret`（或引用 KMS/加密占位符，由运维注入）、`signAlgorithm`、`encryptFields`、`aesKey` / `rsaPublicKey` / `rsaPrivateKey`（按厂商二选一或组合）、`httpTimeoutMs`、`maxRetries`、`circuitBreaker` 阈值、`callbackPath` 白名单。环境通过 Nacos `namespace` + `spring.profiles` / 等价区分 dev/test/prod。
- **理由**：满足「多环境一键切换、多家资金方动态配置、代码零硬编码」；敏感值不进 Git。
- **备选**：仅用环境变量。**未采纳**：多资金方与热切换能力差。

### D3：Java → Go 同步 API 形态

- **内容**：`fund-flow-service` / `repayment-service` 通过 Feign（或 RestTemplate + 拦截器）调用 Go 网关 REST：`POST /internal/v1/disburse`、`POST /internal/v1/repay`、`POST /internal/v1/withhold`（命名可再统一），请求体为业务域 DTO（订单号、用户 ID、金额、期数、银行卡 token 或脱敏后的绑卡引用 ID 等），**不包含**资金方 AppSecret。响应返回受理态（如 `ACCEPTED` + `gatewayRequestId`），终态依赖异步回调或查询补偿。
- **理由**：与资金方「异步终态」一致，避免 Java 长阻塞；网关统一生成对外幂等键。
- **备选**：同步直到资金方返回终态。**未采纳**：多数资方仅异步通知。

### D4：异步回调 → Java 的桥接方式

- **内容**：资金方回调 `POST` 至 Go 对外 URL（可由 APISIX 反代仅暴露 `/fund/callback/{providerId}`）；网关验签、解析、落库（至少原始报文摘要 + 幂等键 + 状态），再二选一或组合：**（a）** 调用 Java 内部回调接口（带 `X-Internal-Sign`）；**（b）** 向 RocketMQ 投递标准领域事件（如 `FUND_DISBURSED_EVENT`、`REPAYMENT_SETTLED_EVENT`），由现有消费者更新订单、分期、额度与画像。
- **理由**：（b）与现有 `microservice-fund-flow` / `microservice-repayment` 事件驱动一致；（a）便于低延迟与排查。**推荐默认（b）为主、（a）为可选同步通知**，具体在实现阶段二选一锁定。
- **备选**：仅 MQ。**未采纳**：部分场景需同步确认写库顺序，保留 HTTP 桥接选项。

### D5：幂等与防重

- **内容**：网关侧以 `(providerId, businessOrderNo, operationType)` 或资金方要求的 `requestNo` 建立唯一约束/Redis SETNX，在重试与重复回调时短路。Java 侧保留现有 `idmpToken` + Redis 锁（用户侧连点）；网关侧防资金方侧重复放款/重复代扣。
- **理由**：双重幂等覆盖用户端与渠道端。

### D6：`batch-service` 代扣调度改造

- **内容**：`batch-service` 中「每日自动代扣」任务改为调用 Go 网关 `POST /internal/v1/withhold`（或批量接口），携带与主动还款一致的业务单号维度；不再直接 Feign Java `repayment-service` 内部扣款接口。
- **理由**：与 proposal 中 `scheduler-go` 规格语义一致（仓库目录为 `go/batch-service`，实现上对齐该调度进程）。

### D7：观测与审计

- **内容**：网关对每笔请求/回调记录 traceId（与 Java 透传 `X-Trace-Id`）、资金方请求号、HTTP 状态、验签结果、熔断状态；原始报文全文不进日志，仅 SHA-256 摘要 + 非敏感字段。
- **理由**：合规审计与排障平衡。

## Risks / Trade-offs

- **[Risk] 单点瓶颈**：所有资金流量经 Go 网关 → **Mitigation**：网关无状态水平扩展；按 `providerId` 分片或限流；资金方侧配额监控。
- **[Risk] Nacos 误配或泄露**：错误 namespace 或过度日志 → **Mitigation**：配置变更审计、启动时强校验必填项、密钥仅内存、日志脱敏扫描。
- **[Risk] 回调伪造 / 重放**：恶意请求冒充资金方 → **Mitigation**：验签 + IP 白名单（若资方提供）+ 幂等表 + 时间窗。
- **[Risk] 异步终态延迟**：Java 长时间处于「受理中」→ **Mitigation**：定时查询任务由网关或 Java 触发（仅经网关转发查询 API），与回调双写一致。
- **[Trade-off] 引入新运维对象**：多一套 Go 部署与监控 → 用统一 Docker Compose / K8s chart 与现有 `batch-service` 对齐。

## Migration Plan

1. **阶段 0**：新增 `fund-channel-gateway` 骨架 + Nacos 配置模板 + Mock 资金方（测试环境返回固定异步回调），Java 保留旧路径开关（feature flag）。
2. **阶段 1**：`fund-flow-service` 放款路径切换为 Java→Go，Mock 验证全链路事件与流水。
3. **阶段 2**：`repayment-service` 主动还款与 `batch-service` 代扣切换至 Go；关闭 Java 直连资金方客户端。
4. **阶段 3**：生产切流前灰度：按产品或按用户百分比路由；监控错误率与回调延迟；准备回滚开关恢复 Java 旧路径（若仍保留兼容层，否则仅回滚部署版本）。
5. **回滚**：保留上一版本镜像与 Nacos 快照；网关故障时降级为「拒绝新放款/代扣受理」而非错误重试导致重复（依赖幂等表）。

## Open Questions

- 资金方回调终态桥接 Java 的**主路径**最终锁定 MQ 还是 HTTP（或二者并存时的顺序语义）。
- 多家资金方同时启用时，**路由策略**（按产品、按用户、按金额区间）由 Nacos 配置还是由 Java 业务在请求中显式传入 `providerId`。
- 银行卡敏感信息在 Java 与 Go 之间传递是否统一为 **token 化引用**（推荐）而非明文四要素。
- 是否与现有 `gateway-apisix` 规格合并对外回调路由与 WAF 策略。
