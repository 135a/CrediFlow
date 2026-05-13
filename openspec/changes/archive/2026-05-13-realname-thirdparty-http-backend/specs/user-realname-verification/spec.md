# user-realname-verification

## Purpose

定义用户服务侧通过可替换 Provider 对接第三方实名 HTTP 核验的能力边界，包括配置外置、模板化请求与脚本签名占位、环境化 Mock、生产启动强校验、限流幂等及结果落库语义。

## ADDED Requirements

### Requirement: Provider 抽象与模板化 HTTP 占位

系统 MUST 以 `RealnameProvider`（或等价命名）抽象第三方实名调用；在厂商具体协议未定时，系统 MUST 支持通过 **可配置请求体模板（如 JSON 模板）** 拼装姓名、身份证号等字段，并由 **可插拔脚本签名策略**（如 JS/Groovy/内置 SPI，具体实现由设计选定）生成签名或摘要后注入请求。系统 MUST 将「模板变量替换 + 脚本输出写回请求」作为单一扩展点，以便未来新增或更换厂商时仅改配置与脚本，而不修改主流程代码。

#### Scenario: 使用占位配置完成一次核验调用

- **WHEN** Nacos 已下发有效的模板、脚本及连接参数，且当前环境未启用 Mock
- **THEN** 系统 MUST 按模板生成请求体，执行签名脚本，向 `baseUrl` 发起 HTTP 调用，并 MUST 将解析后的结构化结果（是否一致、证件状态、描述、流水号）交给业务落库逻辑

#### Scenario: 更换厂商仅需配置变更

- **WHEN** 运维仅更新 Nacos 中的模板、脚本或 `baseUrl`/`providerType` 等字段
- **THEN** 系统 MUST 在不发布新二进制（若脚本外置）或仅发布脚本包的情况下切换到新厂商协议路径；若签名算法需新依赖，则允许发布但 MUST NOT 要求改写 KYC 主流程控制器逻辑

### Requirement: 配置外置与密钥保护

实名服务商的 `baseUrl`、`appKey`、`appSecret`、连接超时、读超时、模板与脚本引用等参数 MUST 通过配置中心（Nacos）或等价外部配置注入；应用默认配置中 MUST NOT 包含生产密钥明文。代码 MUST 通过 `@ConfigurationProperties`（或等价机制）绑定前缀（如 `crediflow.realname`），并 SHOULD 支持配置刷新以替换厂商。

#### Scenario: 缺少关键配置时拒绝核验

- **WHEN** `enabled=true` 且非 Mock 模式下缺少 `baseUrl` 或脚本/模板无法解析
- **THEN** 系统 MUST 拒绝发起外呼并 MUST 向调用方返回可区分的配置错误（不得静默降级为通过）

### Requirement: 非生产 Mock 与生产启动强断言

在非生产环境，系统 MAY 通过 `mockSuccess=true` 跳过真实 HTTP，并 MUST 仍走与生产相同的落库与审计路径。在生产环境（由 Spring `spring.profiles.active` 或项目约定的等价「生产 profile 集合」判定），若检测到 Mock 开关为开启状态，系统 MUST 在应用启动阶段抛出异常并 MUST 中止启动；该检查 MUST 在对外接受流量之前执行。系统 MUST NOT 在生产进程存活期间以 Mock 模式处理任何实名请求。

#### Scenario: 测试环境启用 Mock 完成联调

- **WHEN** 当前激活 profile 属于非生产，且 `mockSuccess=true`
- **THEN** 系统 MUST 不发起外网 HTTP，并 MUST 返回与成功核验一致的结构化结果及可识别的模拟流水号前缀

#### Scenario: 生产环境误开 Mock 导致启动失败

- **WHEN** 当前激活 profile 判定为生产，且配置中 Mock 为开启
- **THEN** 系统 MUST 抛出致命错误并 MUST 拒绝完成 Spring 上下文启动

### Requirement: 调用超时、网络错误与明确业务失败分支

系统 MUST 为 HTTP 调用设置连接超时与读超时。遇网络超时、DNS 失败、连接拒绝或 HTTP 5xx 时，系统 MUST NOT 将用户 `realname_status` 标记为「核验失败（终态）」；系统 MUST 向调用方返回可重试类错误。遇第三方返回明确的「姓名证件不一致」「证件无效或过期」等业务结论时，系统 MUST 将 `realname_status` 置为失败终态并 MUST 持久化原因码（内部）与用户可读摘要。

#### Scenario: 外呼超时

- **WHEN** 外呼在读超时前未返回完整响应
- **THEN** 系统 MUST NOT 写入实名失败终态，并 MUST 返回重试语义错误码

#### Scenario: 第三方明确不匹配

- **WHEN** 解析后的响应表明姓名与身份证号不一致
- **THEN** 系统 MUST 将 `realname_status` 置为失败，并 MUST 记录第三方流水号（若有）

### Requirement: 限流与防重复提交

实名核验接口 MUST 对每个用户标识实施频率限制（例如按滑动窗口计数），并 MUST 对短时间内的重复提交（相同用户与相同证件号指纹）予以拒绝或合并为幂等响应，以防止撞库与暴力破解。

#### Scenario: 超出频率限制

- **WHEN** 同一用户在配置窗口内调用次数超过阈值
- **THEN** 系统 MUST 拒绝本次请求并 MUST NOT 调用第三方

### Requirement: 实名结果持久化与脱敏

系统 MUST 将实名结论写入 `cf_user_kyc`（或等价表），至少包含：`realname_status`、`realname_verified_at`（成功时）、`realname_provider_txn_no`、证件号密文与掩码展示字段；完整证件号 MUST NOT 以明文写入数据库或日志。成功通过后，系统 MUST 将真实姓名与证件结论与后续绑卡四要素、电子签数据源保持一致性约束（由关联规格约束）。

#### Scenario: 核验成功落库

- **WHEN** 第三方返回一致且证件有效
- **THEN** 系统 MUST 将 `realname_status` 置为已通过，写入核验时间与流水号，并 MUST 以密文存储证件号且对外接口仅返回掩码
