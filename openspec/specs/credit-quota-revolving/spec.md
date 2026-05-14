# credit-quota-revolving

## Purpose

定义循环授信额度账户、线性额度公式与占用 / 冻结语义，由 `credit-risk-service`（或独立 `credit-quota-service`，实现阶段可选）对外提供只读查询与内部调账 API。

## ADDED Requirements

### Requirement: 循环额度账户字段

系统 MUST 维护 `cf_user_credit_quota`，至少包含：`user_id`（唯一）、`total_amount`、`used_amount`、`available_amount`、`frozen_amount`、`currency`、`version`（乐观锁）。系统 MUST 保证恒等式 `available_amount = total_amount − used_amount − frozen_amount` 在任意提交成功后成立。

#### Scenario: 放款占用额度

- **WHEN** 借款放款成功确认一笔本金占用
- **THEN** `used_amount` MUST 增加、`available_amount` MUST 同步减少，且 `version` MUST 递增

### Requirement: 线性额度公式

在授信审核通过且进入额度开通步骤时，系统 MUST 按 `UserQuota = MinQuota + (clamp(TotalScore, 60, 100) − 60) / 40 × (MaxQuota − MinQuota)` 计算初始 `total_amount`；`MinQuota` / `MaxQuota` MUST 来自 Nacos。`TotalScore < 60` 的申请 MUST NOT 写入正额度（应走拒绝或人工路径）。

#### Scenario: 分数钳制

- **WHEN** `TotalScore` 为 95
- **THEN** clamp 后 MUST 按 95 参与计算且不得超过 `MaxQuota`

### Requirement: 额度上限受 Agent / 对话升级影响

当存在有效的 `QUOTA_CAP` 风控升级信号时，系统 MUST 将 `total_amount` 或 `available_amount` 上限限制在 cap 内，并 MUST 写审计记录 `source=CHAT_INTENT` 或等价枚举。

#### Scenario: cap 低于当前已用

- **WHEN** cap 小于当前 `used_amount`
- **THEN** 系统 MUST 将额度置为不可借状态（`available_amount=0` 或 `frozen_amount` 增加）且 MUST NOT 出现负数 `available_amount`

### Requirement: 循环授信额度账户体系

系统 MUST 提供循环授信额度账户体系，不仅支持授信时额度的写入，还 MUST 支持单笔提款借款时的并发安全实时额度扣减与冻结操作。

#### Scenario: 借款通过后的额度冻结

- **WHEN** 一笔借款审批通过并生成借据
- **THEN** 循环额度中心 MUST 从该用户的 `availableAmount` 中实时安全扣减等额的借款本金，并增加 `usedAmount`，保证用户的借款总额不超过总授信额度。
