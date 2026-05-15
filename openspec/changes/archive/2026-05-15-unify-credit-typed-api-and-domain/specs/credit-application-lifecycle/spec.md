## MODIFIED Requirements

### Requirement: 授信申请状态机与持久化

系统 MUST 维护授信申请状态机；状态值 MUST 以封闭枚举（如 `CreditApplicationStatus`）在应用层表达，并与数据库 `cf_credit_application.status` 列（字符串存储）一一对应。写入数据库的值 MUST 为枚举的持久化编码（如 `PENDING_HARD_RULES`、`APPROVED`、`REJECTED` 等），MUST NOT 依赖未在枚举中声明的魔法字符串作为常态流转目标。

#### Scenario: 状态写入与读取一致
- **WHEN** 申请从待硬规则检查流转到待评分或已拒绝
- **THEN** 持久化的 `status` 值 MUST 与 `CreditApplicationStatus` 中声明的编码一致，且可被读回为同一枚举值。

#### Scenario: 查询无记录与库状态区分
- **WHEN** API 需要表达「用户从未申请」
- **THEN** MUST 使用查询层哨兵（如 `NOT_APPLIED`）在响应 DTO 中表达，MUST NOT 将该哨兵写入 `cf_credit_application.status` 列。
