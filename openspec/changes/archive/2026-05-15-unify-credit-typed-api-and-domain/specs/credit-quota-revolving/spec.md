## ADDED Requirements

### Requirement: 授信结果状态枚举

表示用户授信额度账户生命周期状态的字段（如 `cf_credit_result.status`）MUST 在应用层使用封闭枚举（如 `CreditResultStatus`：ACTIVE、FROZEN、EXPIRED）表达；查询「当前有效授信」等逻辑 MUST 基于该枚举而非裸字符串字面量。

#### Scenario: 查询有效授信
- **WHEN** 服务查询用户当前可用的授信结果
- **THEN** MUST 以 `CreditResultStatus.ACTIVE`（或等价编码）作为过滤条件，且与库中存储的字符串编码一致。

#### Scenario: 状态变更可审计
- **WHEN** 授信结果被冻结或过期
- **THEN** 写入数据库的状态值 MUST 为枚举持久化编码之一，并可通过枚举读回。
