## MODIFIED Requirements

### Requirement: 借款申请提交与状态流转

贷款申请服务 MUST 接收借款申请并生成唯一申请单号；在接收申请前 MUST 校验借款人是否已完成 KYC 认证（`step_status=3`），否则 MUST 拦截；MUST 维护申请状态机（至少包含：草稿/已提交/审核中/通过/拒绝/取消）；状态迁移 MUST 合法且可审计。

#### Scenario: 用户未完成 KYC 提交借款
- **WHEN** 尚未通过全部 KYC 认证步骤的用户调用借款申请接口
- **THEN** 系统 MUST 拦截该请求，并返回“尚未通过kyc认证”的错误提示

#### Scenario: 非法状态迁移被拒绝

- **WHEN** 调用方尝试从终态（拒绝或取消）迁移到非允许状态
- **THEN** 系统 MUST 拒绝操作并返回可诊断错误码
