## ADDED Requirements

### Requirement: 基础角色与权限隔离 (RBAC)
系统 MUST 实现简单的基于角色的权限控制体系，将管理员、风控审批员、运营查询人员相互隔离，避免越权操作。

#### Scenario: 拦截越权操作
- **WHEN** 普通运营人员尝试调用 `POST /api/admin/credit/application/{id}/approve` 进行风控人工强行过审操作
- **THEN** 系统 MUST 检查其不具备 `ROLE_RISK_ADMIN` 角色，并 MUST 返回 403 Forbidden 错误
