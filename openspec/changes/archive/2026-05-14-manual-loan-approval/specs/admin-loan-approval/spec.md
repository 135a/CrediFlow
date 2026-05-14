## ADDED Requirements

### Requirement: 人工审核列表与详情
系统 MUST 提供给后台管理员查询待审核单据、已审核单据的能力，并提供完整的贷款申请上下文详情（包括用户基础信息、命中规则详情等），以便进行人工复核。

#### Scenario: 查询待审核列表
- **WHEN** 管理员查询处于 `PENDING_MANUAL_REVIEW` 状态的贷款申请列表
- **THEN** 系统 MUST 返回按时间倒序排列的待审核记录

### Requirement: 人工审核决策与记录
系统 MUST 允许授权管理员对 `PENDING_MANUAL_REVIEW` 状态的单据进行“通过”或“驳回”的决策，并 MUST 强制记录审核人 ID、审核意见与操作时间。

#### Scenario: 管理员审批通过
- **WHEN** 管理员针对某笔待审核申请单提交“审批通过”及审核意见
- **THEN** 系统 MUST 将申请单状态推进到 `APPROVED`，并持久化保存审核审计痕迹

#### Scenario: 管理员审批驳回
- **WHEN** 管理员针对某笔待审核申请单提交“审批驳回”及审核意见
- **THEN** 系统 MUST 将申请单状态更新为 `REJECTED`，并持久化保存审核审计痕迹
