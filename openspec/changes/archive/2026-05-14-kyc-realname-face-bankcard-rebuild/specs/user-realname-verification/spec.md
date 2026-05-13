# user-realname-verification（Delta，BREAKING）

> 二要素第三方核验的能力仍然保留，但在 KYC v2 中**降级为闸门前置**：
> 它不再决定 KYC 通过；KYC 通过还需要 `kyc-face-liveness` 的实人核验同时为 VERIFIED。
> 二要素终态本身仍按原 spec 落地（retryable / terminal failure / success 三态），但执行时机被强制改到 `kyc-eligibility-gate` 之后。

## MODIFIED Requirements

### Requirement: Provider 抽象与模板化 HTTP 占位

系统 MUST 以 `RealnameProvider`（或等价命名）抽象第三方实名调用；在厂商具体协议未定时，系统 MUST 支持通过 **可配置请求体模板（如 JSON 模板）** 拼装姓名、身份证号等字段，并由 **可插拔脚本签名策略**（如 JS/Groovy/内置 SPI，具体实现由设计选定）生成签名或摘要后注入请求。系统 MUST 将「模板变量替换 + 脚本输出写回请求」作为单一扩展点，以便未来新增或更换厂商时仅改配置与脚本，而不修改主流程代码。

**KYC v2 中的额外约束**：`RealnameProvider.verify(...)` MUST 仅在 `kyc-eligibility-gate` 全部通过后才被调用；准入闸门未过 MUST NOT 触发外呼。二要素 VERIFIED 仅意味着「这个身份证属于这个姓名」，MUST NOT 单独驱动 `kyc_passed=1`；`kyc_passed=1` 必须叠加 `kyc-face-liveness` 的 VERIFIED。

#### Scenario: 使用占位配置完成一次核验调用

- **WHEN** Nacos 已下发有效的模板、脚本及连接参数，且当前环境未启用 Mock
- **THEN** 系统 MUST 按模板生成请求体，执行签名脚本，向 `baseUrl` 发起 HTTP 调用，并 MUST 将解析后的结构化结果（是否一致、证件状态、描述、流水号）交给业务落库逻辑

#### Scenario: 二要素 VERIFIED 不等于 KYC 通过

- **WHEN** 二要素调用返回 `effectiveSuccess=true`
- **THEN** 系统 MUST 将 `realname_status=VERIFIED` 但 MUST NOT 直接设置 `kyc_passed=1`；用户 MUST 继续走 step2 人脸核验

#### Scenario: 闸门未过不外呼

- **WHEN** 用户因年龄 / 唯一性 / 黑名单被 `kyc-eligibility-gate` 拒绝
- **THEN** 系统 MUST NOT 调用 `RealnameProvider.verify`；MUST NOT 在 `cf_face_verify_log` 或厂商侧产生任何请求

### Requirement: 调用超时、网络错误与明确业务失败分支

系统 MUST 为 HTTP 调用设置连接超时与读超时。遇网络超时、DNS 失败、连接拒绝或 HTTP 5xx 时，系统 MUST NOT 将用户 `realname_status` 标记为「核验失败（终态）」；系统 MUST 向调用方返回可重试类错误。遇第三方返回明确的「姓名证件不一致」「证件无效或过期」等业务结论时，系统 MUST 将 `realname_status` 置为失败终态并 MUST 持久化原因码（内部）与用户可读摘要。

**KYC v2 中的额外约束**：二要素终态失败 MUST 立即拒绝该用户进入 step2 人脸核验入口；用户 MUST 修改输入并重新走 step1（仍受限流与防撞库保护）。

#### Scenario: 外呼超时

- **WHEN** 外呼在读超时前未返回完整响应
- **THEN** 系统 MUST NOT 写入实名失败终态，并 MUST 返回重试语义错误码

#### Scenario: 二要素终态失败后阻断 step2

- **WHEN** 第三方明确返回姓名证件不一致
- **THEN** 系统 MUST 写 `realname_status=FAILED`；MUST 阻止该用户在未修正前进入 step2 人脸核验

## ADDED Requirements

### Requirement: 二要素与人脸的次序约束

系统 MUST 在 step2 人脸核验入口检查 `realname_status=VERIFIED`；未通过二要素的用户 MUST NOT 进入人脸核验入口。该约束在服务端 MUST 强制执行；MUST NOT 仅依赖前端跳过。

#### Scenario: 跳过二要素直接调用 step2

- **WHEN** 客户端绕过 UI 直接 POST `/api/app/user/kyc/v2/step2`
- **THEN** 系统 MUST 返回 422 或拒绝码；MUST NOT 调用 `FaceVerifyProvider`
