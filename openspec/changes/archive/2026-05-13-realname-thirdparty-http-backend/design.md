## Context

- 当前 `user-service` 的 `UserKycController` 第二步依赖身份证与人脸 Base64，经 `AgentClient` 完成 OCR 与活体模拟，与「权威二要素核验」不等价，且链路依赖 Python Agent。
- 业务要求改为：用户提交**真实姓名 + 身份证号**，由**第三方实名 HTTP 接口**返回一致性及证件状态；配置（URL、密钥、超时）置于 **Nacos**；测试环境可通过开关 **Mock** 成功路径；结果写入 `cf_user_kyc`（或等价结构），并作为绑卡、授信、借款、签约的前置数据源。
- 本设计仅覆盖**后端**实现策略与关键决策；规格级需求见后续 `specs/` 产物。

## Goals / Non-Goals

**Goals:**

- 以可替换的 `RealnameProvider` 抽象对接第三方 HTTP（POST、签名/加密可插拔、统一超时与 JSON 解析）。
- 通过 `spring.cloud.nacos` 绑定的配置属性（或 `@ConfigurationProperties` + Nacos 刷新）管理服务商参数与 Mock 开关。
- 在 `user-service` 提供新的或改造后的 KYC 第二步 API：入参为明文二要素（传输依赖 HTTPS），服务端做身份证规则校验、限流、防重复提交，再调用核验并落库。
- 扩展 `cf_user_kyc` / `UserKyc`：实名状态、核验时间、第三方流水号、证件密文与掩码展示字段；与现有敏感数据加密策略一致。
- 明确与其他模块的集成点：内部查询接口供绑卡/签约/授信校验「已实名」；网关或业务层拦截策略在实现任务中落地。

**Non-Goals:**

- 前端页面、身份证拍照、OCR、活体、Agent 视觉链路。
- 在本迭代内实现完整网关插件或所有下游微服务的拦截代码（仅在设计中约定契约与推荐落点）。
- 直连公安库或自建人脸库。

## Decisions

1. **API 形态：改造 `POST /api/app/user/kyc/step2` 还是新增专用路径**  
   - **决策**：**改造现有 `step2`**，请求体改为 `realName` + `idCardNo`（JSON），删除对 `idCardBase64` / `faceBase64` 及 `AgentClient` 的调用。  
   - **理由**：保持 KYC 三步 URL 稳定，仅变更契约；与提案中 **BREAKING** 一致。  
   - **备选**：新增 `POST /api/app/user/realname/verify` 并由 `step2` 废弃重定向——增加迁移成本，除非需长期并行旧客户端。

2. **实名状态与 `step_status` 的关系**  
   - **决策**：保留 `step_status` 表示「流程进度」；新增 **`realname_status`**（枚举：未提交 / 已通过 / 失败 / 可选：处理中）表达权威核验结果。`step_status` 进入 `2` 的前提是 `realname_status=已通过` 且业务规则满足（如第一步已完成）。  
   - **理由**：避免「流程走到 2 但未通过第三方」的歧义，便于审计与下游只读 `realname_status`。  
   - **备选**：仅用 `step_status`——无法区分「提交失败可重试」与「流程未完成」。

3. **第三方调用封装**  
   - **决策**：定义接口 `RealnameProvider.verify(RealnameVerifyCommand) -> RealnameVerifyResult`；默认实现 `HttpRealnameProvider` 使用 `RestClient` 或 `WebClient`（与项目现有栈一致），由 **`RealnameSignatureStrategy`**（按 `provider-type` 选择 Bean）负责拼参与签名。  
   - **理由**：换供应商时替换策略与配置，无需扩散 if-else。  
   - **备选**：单类 `HttpUtil` 静态方法——难以单测与扩展。

4. **配置来源**  
   - **决策**：`@ConfigurationProperties(prefix = "crediflow.realname")`，字段包括 `enabled`、`mockSuccess`、`providerType`、`baseUrl`、`appKey`、`appSecret`、`connectTimeout`、`readTimeout`；支持 `@RefreshScope` 以便 Nacos 动态刷新（若项目已启用 refresh）。密钥 **禁止** 写入仓库默认值。  
   - **理由**：与 Spring Cloud Alibaba 惯例一致。  
   - **备选**：硬编码 + 环境变量——不满足「服务商可替换、环境可切换」要求。

5. **测试 Mock**  
   - **决策**：`mockSuccess=true` 时跳过 HTTP，返回固定成功结果与可预测的 `providerTxnNo` 前缀（如 `MOCK-`），仍走同一落库与审计路径。生产 profile 启动校验：若 `mockSuccess=true` 则拒绝启动或打致命日志（可配置为严格模式）。  
   - **理由**：集成测试与联调不依赖外网；降低误开 Mock 上生产的风险。

6. **限流与防重复**  
   - **决策**：按 `userId` 的滑动窗口限流（Redis + Lua 或 Bucket4j Redis）；防重复采用 **Idempotency-Key** 请求头（可选）+ 短时间窗口内相同「用户 + 证件号哈希」拒绝重复提交。  
   - **理由**：身份证属高敏，暴力试错成本高，必须限频。  
   - **备选**：仅数据库唯一约束——无法挡瞬时并发洪峰。

7. **证件号存储**  
   - **决策**：库表存 **密文**（沿用 common 中已有 `CryptoTypeHandler` 或等价）+ **`id_card_mask`** 供接口展示；可选存 **`id_card_fingerprint`**（HMAC-SHA256，盐来自配置）用于等幂比对而不解密。  
   - **理由**：满足脱敏与检索平衡。  
   - **备选**：仅哈希不可展示后四位——产品体验差。

8. **调用失败与第三方返回「不确定」**  
   - **决策**：网络超时、连接失败、5xx：**不**将 `realname_status` 置为失败，返回业务码「请稍后重试」；第三方明确返回「不匹配 / 无效 / 过期」：**置失败**并记录原因码。  
   - **理由**：避免误伤用户征信式「失败锁定」。  
   - **备选**：一律失败——投诉与客诉成本高。

## Risks / Trade-offs

- **[Risk]** 破坏性变更导致旧 App 无法调 KYC。  
  → **Mitigation**：版本化文档与错误码；必要时短期保留旧接口别名（若产品坚持，可作为 Open Question 追加一次迭代）。

- **[Risk]** Nacos 密钥泄露。  
  → **Mitigation**：使用 Nacos 加密、最小权限账号；日志与 Actuator 屏蔽 properties；代码审查禁止默认值提交密钥。

- **[Risk]** Mock 误上生产。  
  → **Mitigation**：profile 校验 + 发布检查清单；`mockSuccess` 仅在 `dev/test` 配置文件中存在。

- **[Trade-off]** 二要素通过仍不能完全杜绝冒用（黑产持有他人证件信息）。  
  → 后续可由风控叠加活体/银行卡四要素；本设计不展开。

## Migration Plan

1. **数据库**：Flyway/Liquibase 脚本为 `cf_user_kyc` 增加 `realname_status`、`realname_verified_at`、`realname_provider_txn_no`、`id_card_mask` 等列；视情况将原 `id_card_no` 改为加密列或新增 `_cipher` 列并数据迁移（若已有明文历史数据，需一次性加密脚本）。
2. **配置**：在 Nacos 各环境写入 `crediflow.realname.*`；测试环境 `mockSuccess=true`，生产为 false。
3. **部署**：先发布 `user-service`（新契约），再协调 App 发版；若存在仅旧客户端，需灰度或双读兼容期（由产品决定）。
4. **回滚**：保留上一版本镜像；DB 新列可空，回滚旧代码不依赖新列写入。

## Open Questions

- 第三方厂商最终协议（签名算法、字段名、加密方式）未定时，`HttpRealnameProvider` 是否先以 **「可配置 JSON 模板 + 脚本签名」** 占位，还是对接第一家厂商后再抽象？
- `step_status` 是否要求 **实名通过后才能进入 step3（绑卡）**：本设计倾向 **是**，需在 specs 中写成强制场景。
- 生产环境 **Mock 严格校验** 是否启用 Spring `Environment` 断言（硬失败）还是仅告警——需与运维规范对齐。
