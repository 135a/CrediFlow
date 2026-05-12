## MODIFIED Requirements

### Requirement: 授信评估与规则校验

授信风控服务 MUST 基于用户资料、征信占位数据与内部规则集输出授信建议；用户提交申请时 MUST 先行验证用户是否通过 KYC 认证（`step_status=3`），未通过时 MUST 拦截申请；验证通过后 MUST 先行落库 `PENDING` 状态记录；MUST 执行异步风控规则校验并在机审完成后更新状态流转。此外，系统 MUST 将 KYC 认证中提取的真实年龄、职业、月收入等核心属性注入到 Agent 决策的输入上下文中。

#### Scenario: 用户未完成 KYC 提交申请
- **WHEN** 尚未通过全部 KYC 认证步骤的用户调用授信申请接口
- **THEN** 系统 MUST 拦截该请求，并返回“尚未通过kyc认证”的错误提示

#### Scenario: 用户提交授信申请

- **WHEN** 用户已通过 KYC 并调用授信申请接口
- **THEN** 系统 MUST 立即返回带有单号的 `PENDING` 状态响应，并避免前端同步阻塞等待大模型

#### Scenario: 规则拒绝

- **WHEN** 用户触发硬性拒绝规则或被大模型机审拒绝
- **THEN** 系统 MUST 将申请流水状态更新为拒绝（如 `REJECTED`）且 MUST 记录规则命中代码或大模型 `auditReason` 审计信息
