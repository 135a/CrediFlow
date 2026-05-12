## Purpose

TBD
## Requirements
### Requirement: 借款申请提交与状态流转

贷款申请服务 MUST 接收借款申请并生成唯一申请单号；在接收申请前 MUST 校验借款人是否已完成 KYC 认证（`step_status=3`），否则 MUST 拦截；MUST 维护申请状态机（至少包含：草稿/已提交/审核中/通过/拒绝/取消）；状态迁移 MUST 合法且可审计。

#### Scenario: 用户未完成 KYC 提交借款
- **WHEN** 尚未通过全部 KYC 认证步骤的用户调用借款申请接口
- **THEN** 系统 MUST 拦截该请求，并返回“尚未通过kyc认证”的错误提示

#### Scenario: 非法状态迁移被拒绝

- **WHEN** 调用方尝试从终态（拒绝或取消）迁移到非允许状态
- **THEN** 系统 MUST 拒绝操作并返回可诊断错误码

### Requirement: 资料审核与资格校验

系统 MUST 支持资料审核记录（审核人、时间、结论、备注）；在放款资格校验前 MUST 校验授信有效性与合同签署完成情况（与合同服务协作的契约由集成层实现）。

#### Scenario: 未授信通过不可进入放款队列

- **WHEN** 申请单尝试进入放款资格校验且授信未通过
- **THEN** 系统 MUST 拒绝并 MUST 记录原因

### Requirement: 幂等与重复提交防护

同一幂等键下的重复提交 MUST 返回同一业务结果且不重复创建申请单。

#### Scenario: 客户端重试重复提交

- **WHEN** 客户端使用相同 `Idempotency-Key` 重复提交同一申请内容
- **THEN** 系统 MUST 返回与首次成功一致的业务单号且 MUST NOT 创建第二张申请单

