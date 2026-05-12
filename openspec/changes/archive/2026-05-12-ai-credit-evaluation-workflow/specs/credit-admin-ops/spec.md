## ADDED Requirements

### Requirement: 申请记录的后台范围查询
系统 MUST 提供管理端接口，支持基于申请发起时间范围（开始时间、结束时间）对所有 `CreditApplication` 记录进行分页检索和查看。

#### Scenario: 按日期范围过滤申请记录
- **WHEN** 后台管理员输入开始时间与结束时间请求查询
- **THEN** 系统 MUST 仅返回在该时间区间内创建的授信申请列表及状态

### Requirement: 人工干预强行审批
系统 MUST 允许授权的管理端用户对被判定为 `REJECTED` 的申请记录进行人工干预，强行修改为通过并触发额度发放机制。

#### Scenario: 人工强行通过被拒申请
- **WHEN** 管理员调用干预审批接口对一笔 `REJECTED` 状态的申请执行通过操作，并填写备注
- **THEN** 系统 MUST 将申请状态更为 `APPROVED`，MUST 将操作备注追加到 `auditReason` 字段，且 MUST 发放相应额度

#### Scenario: 拒绝针对已通过状态的干预
- **WHEN** 管理员尝试干预审批一笔状态已经是 `APPROVED` 的申请
- **THEN** 系统 MUST 拦截操作并返回幂等成功或相应的业务错误提示
