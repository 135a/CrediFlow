# microservice-user（Delta）

## MODIFIED Requirements

### Requirement: 用户画像采集边界

用户服务 MUST 仅采集配置中显式启用的画像字段；画像变更 MUST 可追溯到操作者与时间；用户服务 MUST 通过独立的 KYC API 与 `cf_user_kyc` 数据结构采集和管理用户的身份、职业、收入及收款账户信息，并禁止在未经 KYC 认证通过的情况下外传授信凭证。

#### Scenario: 更新画像字段

- **WHEN** 运营或系统任务更新用户画像字段
- **THEN** 系统 MUST 写入审计日志包含变更前后摘要（不含敏感明文）

#### Scenario: 用户完成三步 KYC 认证

- **WHEN** 用户依次通过基础信息填写、姓名与身份证二要素第三方实名核验（`realname_status` 为已通过）、收款账号绑定接口
- **THEN** 系统 MUST 在 `cf_user_kyc` 更新相应的状态 (`step_status=3`)，并标识为 KYC 认证成功
