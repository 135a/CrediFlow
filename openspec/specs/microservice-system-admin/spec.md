## Purpose

TBD

## Requirements

### Requirement: 角色权限模型

系统权限服务 MUST 提供角色、权限点与菜单（或资源标识）绑定能力；授权变更 MUST 审计。

#### Scenario: 分配权限成功

- **WHEN** 管理员为角色新增权限点
- **THEN** 系统 MUST 持久化绑定关系并 MUST 写入审计日志包含操作者与变更内容摘要

### Requirement: 管理端访问控制

所有后台管理 API MUST 在网关与服务侧双重校验管理端角色；普通用户令牌 MUST NOT 调用管理端高危接口。

#### Scenario: 普通用户访问被拒绝

- **WHEN** 普通用户 JWT 调用仅允许管理员的路由
- **THEN** 系统 MUST 返回 403 且 MUST 记录安全事件

### Requirement: 操作日志审计

系统 MUST 记录关键运营操作（权限变更、手工调额占位、手工结清占位等）到审计日志，字段至少包含：时间、操作者、对象 id、动作、结果。

#### Scenario: 查询审计日志

- **WHEN** 授权审计员按时间范围查询操作日志
- **THEN** 系统 MUST 返回分页结果且 MUST 对敏感字段脱敏
