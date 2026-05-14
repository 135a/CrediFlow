## MODIFIED Requirements

### Requirement: 循环授信额度账户体系
系统 MUST 提供循环授信额度账户体系，不仅支持授信时额度的写入，还 MUST 支持单笔提款借款时的并发安全实时额度扣减与冻结操作。

#### Scenario: 借款通过后的额度冻结
- **WHEN** 一笔借款审批通过并生成借据
- **THEN** 循环额度中心 MUST 从该用户的 `availableAmount` 中实时安全扣减等额的借款本金，并增加 `usedAmount`，保证用户的借款总额不超过总授信额度。
