## 1. 配置模型与生产启动强校验

- [x] 1.1 在 `user-service` 增加 `@ConfigurationProperties(prefix = "crediflow.realname")`（含 `enabled`、`mockSuccess`、`providerType`、`baseUrl`、`appKey`、`appSecret`、连接/读超时、模板与脚本引用路径等），并提供本地 `application-*.yml` 示例（不含生产密钥）。
- [x] 1.2 实现 `ApplicationContextInitializer` 或 `EnvironmentPostProcessor` / 启动 Runner：当 **判定为生产 profile** 且 `crediflow.realname.mock-success=true` 时，**抛出异常并中止启动**；规格要求绝不允许生产在 Mock 下运行。
- [x] 1.3 文档化「生产 profile 集合」判定规则（例如 `prod` 或 `spring.profiles.active` 包含 `prod`），与运维发布检查清单对齐。

## 2. 数据库与实体

- [x] 2.1 编写 `cf_user_kyc` 迁移脚本：新增 `realname_status`、`realname_verified_at`、`realname_provider_txn_no`、`id_card_mask`、可选 `id_card_fingerprint`；明确 `id_card_no` 密文存储策略（TypeHandler 或新列）。
- [x] 2.2 更新 `UserKyc` 实体与 MyBatis-Plus 映射；必要时新增枚举类表示 `realname_status`。
- [x] 2.3 若有历史明文证件号，编写一次性数据修复或加密回填任务（可脚本化），并记录回滚策略。

## 3. Provider 层（模板 + 脚本签名占位）

- [x] 3.1 定义 `RealnameProvider`、`RealnameVerifyCommand`、`RealnameVerifyResult`（含匹配标志、证件状态、流水号、内部原因码、是否可重试）。
- [x] 3.2 实现 `RealnameSignatureStrategy` SPI 或按 `providerType` 路由的 Bean，支持从配置加载 **JSON 请求体模板**（占位符替换姓名、证件号、时间戳等）。
- [x] 3.3 实现脚本签名占位（如 Groovy / GraalJS / 内置 Java 策略接口）：输入为「待签名字符串 + secret 上下文」，输出为签名字段，写入模板指定键；提供 **no-op / demo** 实现供联调。
- [x] 3.4 实现 `HttpRealnameProvider`：使用 `RestClient` 或 `WebClient` 发起 POST，应用超时，解析响应 JSON 映射到 `RealnameVerifyResult`；对 5xx/超时映射为「可重试、不写失败终态」。
- [x] 3.5 实现 `MockRealnameProvider`：当 `mockSuccess=true` 且非生产启动阻断逻辑已满足时，返回成功与 `MOCK-` 前缀流水号，不发起 HTTP。

## 4. 应用服务与 KYC 第二步改造

- [x] 4.1 改造 `UserKycController` 的 `POST .../kyc/step2`：请求体改为 `realName`、`idCardNo`；移除 `idCardBase64`、`faceBase64` 及对 `AgentClient` 的调用。
- [x] 4.2 实现服务端身份证格式与校验位校验、非空与长度校验；敏感日志脱敏。
- [x] 4.3 在应用服务中编排：校验 → 限流/幂等 → 调用 `RealnameProvider` → 按规格区分成功、明确失败、可重试异常 → 更新 `UserKyc`（含 `realname_status`、`step_status` 规则：仅实名通过后将 `step_status` 置为 2）。
- [x] 4.4 改造 `POST .../kyc/step3`：在入口校验 `realname_status` 为已通过，否则拒绝（与 delta spec 一致）。
- [x] 4.5 视需要更新 `GET .../kyc/status` 响应，对外返回掩码证件号与不暴露内部原因码的摘要。

## 5. 限流与防重复提交

- [x] 5.1 接入 Redis（或已有组件）实现按 `userId` 的滑动窗口/固定窗口限流，阈值与窗口来自配置。
- [x] 5.2 实现防重复：支持可选 `Idempotency-Key` 头；若无则使用「用户 + 证件指纹 + 短 TTL」拒绝重复提交或返回同一幂等结果。
- [x] 5.3 为限流与幂等编写单元测试或集成测试（含 Mock Provider）。

## 6. 可观测与安全

- [x] 6.1 全链路 traceId 透传至外呼 HTTP 头；禁止在日志中打印完整证件号与 secret。
- [x] 6.2 外呼失败与成功均记录审计事件（用户 ID、流水号、结果枚举、耗时），不含明文证件。

## 7. 对下游的契约与占位（本迭代最小集）

- [x] 7.1 在 `user-service` 增加或扩展 **内部 Feign/HTTP** 接口：供绑卡、授信、合同等服务查询 `userId` 对应 `realname_status`（或返回布尔 `verified`），并在 README 或模块内 ADR 说明调用约定。
- [x] 7.2 （可选，若本迭代包含）在 `credit-risk-service`、`loan-application-service` 等入口增加 `realname_status` 校验占位或 TODO 链接至本变更 tasks，避免规格与代码长期漂移。

## 8. 测试与验证

- [x] 8.1 单元测试：`HttpRealnameProvider` 对超时、5xx、明确业务失败的映射；`MockRealnameProvider` 路径。
- [x] 8.2 单元/集成测试：生产 profile + `mockSuccess=true` 时 **启动失败**。
- [x] 8.3 集成测试：`mockSuccess=true` 下 step2 成功落库与 step3 门禁。
- [x] 8.4 运行 `openspec validate realname-thirdparty-http-backend` 并在 PR 描述中引用本变更与破坏性 API 说明。
