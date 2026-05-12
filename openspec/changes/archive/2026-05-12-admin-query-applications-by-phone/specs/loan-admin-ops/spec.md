## ADDED Requirements

### Requirement: 借款申请的后台管理视图
系统 MUST 提供管理端接口 `/api/admin/loan/applications`，支持运营管理员对所有 `LoanApplication` 记录进行分页检索，支持按创建时间区间与用户手机号码过滤。

#### Scenario: 运营人员分页浏览近期借款单
- **WHEN** 运营人员发起不带过滤条件或仅带时间范围的分页查询
- **THEN** 系统 MUST 按创建时间倒序返回符合条件的借款申请详细列表

#### Scenario: 运营人员按手机号查询特定用户的借款单
- **WHEN** 运营人员在查询条件中填入了用户手机号 `phone`
- **THEN** 系统 MUST 通过用户服务内部接口获取对应的 `userId`，并在借款库中只返回属于该用户的借款申请流水；若该手机号未注册则快速返回空列表
