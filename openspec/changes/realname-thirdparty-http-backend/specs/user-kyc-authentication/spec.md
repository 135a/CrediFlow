# user-kyc-authentication（Delta）

## MODIFIED Requirements

### Requirement: 三步 KYC 认证生命周期

系统 MUST 提供一个多步骤的 KYC 认证流程，分为：基础信息填写、姓名与身份证号二要素实名核验（经可配置第三方 HTTP Provider）、收款账号绑定。系统 MUST 记录用户的当前完成步骤（`step_status`），并支持用户在中断后恢复到上一个未完成的步骤。系统 MUST 维护独立的 `realname_status` 表示第三方实名核验结论；在未通过实名核验前，系统 MUST NOT 将 `step_status` 推进到允许进入收款账号绑定（第三步）或更高业务风险状态。

#### Scenario: 用户首次发起 KYC

- **WHEN** 用户触发 KYC 流程，且 `step_status` 为 0
- **THEN** 系统 MUST 引导用户进入第一步（基础信息填写）

#### Scenario: 断点续传恢复 KYC

- **WHEN** 用户的 `step_status` 为 1（基础信息已填）并重新进入 KYC 流程
- **THEN** 系统 MUST 跳过第一步，直接进入第二步（二要素实名核验）

#### Scenario: 未通过实名不得进入绑卡步骤

- **WHEN** 用户调用第三步收款账号绑定接口，且 `realname_status` 未为已通过
- **THEN** 系统 MUST 拒绝请求并 MUST NOT 更新 `step_status` 为 3

## REMOVED Requirements

### Requirement: 大模型图像 OCR 解析

**Reason**：实名权威结论改由第三方二要素 HTTP 核验提供；影像 OCR 与拍照上传不再作为 KYC 主链路组成部分，避免与 Provider 结论重复且降低链路复杂度。

**Migration**：客户端删除身份证图片与人脸 Base64 提交；改为在第二步提交 `realName` 与 `idCardNo` 文本字段并消费新的错误码与限流语义。历史已 OCR 落库数据由数据团队按需迁移或标记（若有）。
