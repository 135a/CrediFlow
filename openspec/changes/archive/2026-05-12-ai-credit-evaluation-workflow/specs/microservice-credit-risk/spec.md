## MODIFIED Requirements

### Requirement: 授信评估与规则校验

授信风控服务 MUST 基于用户资料、征信占位数据与内部规则集输出授信建议；用户提交申请时 MUST 先行落库 `PENDING` 状态记录；MUST 执行异步风控规则校验并在机审完成后更新状态流转。

#### Scenario: 用户提交授信申请

- **WHEN** 用户调用授信申请接口
- **THEN** 系统 MUST 立即返回带有单号的 `PENDING` 状态响应，并避免前端同步阻塞等待大模型

#### Scenario: 规则拒绝

- **WHEN** 用户触发硬性拒绝规则或被大模型机审拒绝
- **THEN** 系统 MUST 将申请流水状态更新为拒绝（如 `REJECTED`）且 MUST 记录规则命中代码或大模型 `auditReason` 审计信息
