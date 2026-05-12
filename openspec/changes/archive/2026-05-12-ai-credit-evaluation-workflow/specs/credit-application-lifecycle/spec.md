## ADDED Requirements

### Requirement: 授信申请状态机流转
系统 MUST 为用户的每一笔授信额度申请创建 `CreditApplication` 记录；申请 MUST 包含 `PENDING` (机审中)、`APPROVED` (机审通过)、`REJECTED` (机审拒绝) 三种状态。

#### Scenario: 发起授信申请落库为机审中
- **WHEN** 用户调用接口发起授信额度申请
- **THEN** 系统 MUST 在数据库插入一条状态为 `PENDING` 的申请记录，并返回申请受理成功的响应，而 MUST NOT 阻塞等待模型结果

#### Scenario: 大模型响应拒绝
- **WHEN** 异步风控机审判定该申请不予通过
- **THEN** 系统 MUST 将该申请记录更新为 `REJECTED`，并 MUST 记录大模型返回的具体原因到 `auditReason` 字段

#### Scenario: 大模型响应通过
- **WHEN** 异步风控机审判定该申请可以通过并给出建议额度
- **THEN** 系统 MUST 将该申请记录更新为 `APPROVED`，并基于大模型的建议生成最终的 `CreditResult` 可用额度
