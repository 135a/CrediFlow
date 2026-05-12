## Purpose

TBD

## Requirements

### Requirement: 还款计划生成

还款服务 MUST 基于放款结果生成还款计划（期次、到期日、应还本金/利息/手续费拆分）；计划 MUST 可查询且变更 MUST 留痕。

#### Scenario: 生成计划后查询

- **WHEN** 放款完成事件被消费并生成还款计划
- **THEN** 系统 MUST 提供按借据号查询计划列表且金额合计 MUST 与合同/放款口径一致

### Requirement: 主动还款与幂等

系统 MUST 支持主动还款；还款请求 MUST 支持幂等键；重复请求 MUST NOT 重复入账。

#### Scenario: 重复还款请求

- **WHEN** 同一幂等键的还款请求被重复提交
- **THEN** 系统 MUST 返回同一还款结果且 MUST NOT 产生第二条成功还款流水

### Requirement: 分期与还款方式适配

系统 MUST 支持多种还款方式配置（至少包含：银行卡代扣占位、主动支付占位）；方式切换 MUST 记录审计。

#### Scenario: 不支持的方式被拒绝

- **WHEN** 用户选择当前产品未启用的还款方式
- **THEN** 系统 MUST 拒绝并返回明确原因
