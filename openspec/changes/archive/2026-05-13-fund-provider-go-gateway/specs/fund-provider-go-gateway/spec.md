# fund-provider-go-gateway

## Purpose

定义 Go 侧统一资金方接入网关（`fund-channel-gateway`）的能力边界：作为本平台对持牌资金方 HTTPS API 的唯一出站；承载 Nacos 外置配置、JSON 报文、请求加签与响应验签、敏感字段加解密、幂等、超时重试与熔断、异步回调处理及对 Java 业务侧的安全桥接。明确用户、平台、资金方、支付代扣通道四类角色中，资金方唯一出资/收款，通道仅为转账工具。

## ADDED Requirements

### Requirement: 角色边界与资金路径

系统 MUST 在规格与实现层区分四类角色：用户（仅与前台交互）、我方平台（Java + Go 微服务）、持牌资金方（唯一出资与收款主体、提供开放 API）、支付代扣通道（银联/银行代扣等，仅为资金转账工具，NOT 资金方）。放款与还款导致的银行资金划转 MUST 仅在资金方对公账户与用户绑定储蓄卡之间发生；平台服务器、平台自有银行账户 MUST NOT 作为资金过路节点。

#### Scenario: 放款资金路径

- **WHEN** 放款请求被资金方受理并最终成功
- **THEN** 资金 MUST 自资金方对公账户划入用户绑定储蓄卡；系统 MUST NOT 将资金先划入平台自有账户再二次转出

#### Scenario: 还款资金路径

- **WHEN** 还款或代扣被资金方成功执行
- **THEN** 资金 MUST 自用户绑定储蓄卡原路回流至资金方对公账户；系统 MUST NOT 截留或中转至平台自有账户

### Requirement: Java 禁止直连资金方

Java 业务微服务（含 `fund-flow-service`、`repayment-service` 等）MUST NOT 以任何 HTTP 客户端直连资金方域名或持有资金方 `AppSecret` / 签名私钥用于对外调用。所有放款、还款、查询、代扣及回调验签 MUST 由 Go 资金网关统一执行；Java MUST 仅通过内网 API 向网关下发业务参数并消费网关桥接的终态结果。

#### Scenario: Java 发起放款受理

- **WHEN** `fund-flow-service` 需要发起放款
- **THEN** 系统 MUST 仅调用 Go 资金网关内部放款接口；MUST NOT 在 JVM 进程内组装面向资金方的带签请求

### Requirement: Nacos 外置配置与禁止硬编码

资金方 `baseUrl`、各环境 `AppKey`、`AppSecret`、加签算法标识、AES/RSA 密钥材料、HTTP 连接/读超时、重试次数、熔断阈值、回调路径白名单等参数 MUST 全部从 Nacos（或项目约定的等价配置中心）加载；应用代码与默认配置文件（随二进制发布）中 MUST NOT 包含生产可用的资金方密钥或固定生产 `baseUrl`。系统 MUST 支持按 namespace/profile 切换开发/测试/生产，并 MUST 支持同一运行实例内多家资金方（如按 `providerId` 分配置段）动态启用与替换。

#### Scenario: 缺少关键配置拒绝受理

- **WHEN** 某 `providerId` 启用放款或还款，但 Nacos 中缺少该 provider 的 `baseUrl` 或签名必需密钥
- **THEN** 网关 MUST 拒绝该笔受理请求并 MUST 返回可区分「配置缺失」的错误码；MUST NOT 以占位默认密钥继续外呼

### Requirement: 传输与安全机制

对资金方的对外调用 MUST 使用标准 HTTPS；请求与响应载荷 MUST 使用 JSON。系统 MUST 对请求实施加签、对响应实施验签以防篡改与伪造；对配置中声明的敏感字段 MUST 使用 AES 或 RSA（按资金方协议）加解密。网关 MUST 为每笔资金类操作携带业务级幂等键（或与资金方约定字段映射），并与网关侧持久化或分布式存储结合，保证重复请求与重复回调 MUST NOT 导致重复放款或重复代扣。

#### Scenario: 响应验签失败

- **WHEN** 资金方同步 HTTP 响应验签失败
- **THEN** 网关 MUST 将该响应视为不可信；MUST NOT 向 Java 投递成功终态；MUST 记录安全审计事件并可触发人工核对流程

#### Scenario: 重复受理请求

- **WHEN** Java 使用相同业务幂等键对同一笔借据重复调用放款受理接口
- **THEN** 网关 MUST 返回与首次受理一致的幂等响应或明确「已受理」语义；MUST NOT 向资金方发起第二次有效放款请求

### Requirement: 超时重试与熔断降级

网关 MUST 为资金方 HTTP 调用配置连接超时与读超时；对幂等安全的可重试错误（如超时、连接重置、可配置 HTTP 5xx）MUST 实施有限次数退避重试。网关 MUST 对连续失败实施熔断，在打开状态下 MUST 快速失败并 MUST NOT 压垮资金方；熔断期间新请求 MUST 返回可观测的降级错误，且 MUST NOT 静默吞没。

#### Scenario: 熔断打开时拒绝浪涌

- **WHEN** 熔断器处于打开状态且新业务请求到达
- **THEN** 网关 MUST 立即拒绝并 MUST 返回熔断相关错误码；MUST NOT 同步阻塞等待远端恢复

### Requirement: 异步回调统一入口与桥接 Java

资金方异步结果通知 MUST 投递至 Go 网关对外回调 URL（可由边缘网关反代）；网关 MUST 完成验签（及必要解密）后解析成功/失败终态，持久化原始报文摘要（非全文明文日志）与幂等处理标记，再 MUST 通过项目选定的一种或组合机制将终态桥接至 Java：内带 `X-Internal-Sign` 的 HTTP 回调，和/或向 RocketMQ 投递标准领域事件（如 `FUND_DISBURSED_EVENT`、`REPAYMENT_SETTLED_EVENT`）。Java 业务服务 MUST 仅从上述桥接通道接收终态，MUST NOT 另行暴露无验签的资金方直连回调面。

#### Scenario: 回调验签通过后投递事件

- **WHEN** 网关收到资金方放款结果回调且验签通过且幂等键未处理过
- **THEN** 网关 MUST 向 MQ 投递（或调用 Java 回调接口）包含借据号、终态、资金方流水号、金额与时间戳的结构化载荷；MUST 标记该回调幂等键已处理

#### Scenario: 回调签名无效

- **WHEN** 资金方回调签名验证失败
- **THEN** 网关 MUST 返回非 2xx 或业务约定失败语义、MUST NOT 更新业务终态、MUST 记录安全审计

### Requirement: 对内同步受理 API

网关 MUST 对内暴露经内网保护的 REST（或等价）接口，供 Java 下发：放款受理、主动还款受理、到期代扣受理、以及查询类请求（若资金方提供）。请求体 MUST 仅包含业务域字段（订单号、用户标识、金额、期数、绑卡引用 token 等）；MUST NOT 要求 Java 传入资金方 `AppSecret`。同步响应 MUST 表达受理结果（如已受理、配置错误、熔断拒绝），终态 MUST 依赖异步回调或查询补偿接口由网关统一转发。

#### Scenario: 内网调用缺少签名

- **WHEN** Java 调用网关内部接口但未携带符合 `internal-api-security` 约定的内网签名头
- **THEN** 网关 MUST 返回 401 并 MUST 拒绝处理
