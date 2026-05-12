## Why

当前系统的额度申请机制较为简略，调用大模型后直接生成授信额度，缺乏对风控机审中间状态（如“机审中”、“机审通过”、“机审拒绝”）的跟踪与记录。为了完善企业级信贷审批流程，提供完整的风控可追溯性，并在极端情况下提供人工干预（拒件捞回）的兜底能力，系统必须引入明确的机审状态机并对所有授信申请记录进行持久化存储。

## What Changes

- 引入申请记录表（`CreditApplication`），持久化保存用户的每一笔额度申请及其机审决策过程和原因。
- **BREAKING**: 重构当前的授信申请主流程。提交申请时先生成 `PENDING`（机审中）的记录；大模型裁决建议通过则更新为 `APPROVED`（机审通过）并实际发放额度，拒绝则置为 `REJECTED`（机审拒绝）。
- 在管理后台新增基于日期时间范围（Date/Time Range）的申请记录列表查询接口。
- 在管理后台新增人工干预审批接口，允许运营人员对大模型标记为 `REJECTED` 的拒件进行人工强行通过操作，并生效额度。

## Capabilities

### New Capabilities
- `credit-application-lifecycle`: 定义额度申请的异步生命周期（从发起申请、机审到完结状态流转）及记录规则。
- `credit-admin-ops`: 定义后台管理端对授信申请记录的检索条件支持及人工干预审批权限规则。

### Modified Capabilities
- `microservice-credit-risk`: 修改现有的 `applyCredit` 接口逻辑契约，从“直接创建激活额度”变更为“创建申请记录并基于 Agent 响应驱动状态流转”。

## Impact

- **数据层**: 需要新增 `cf_credit_application` 申请流水表，修改原有额度表 `cf_credit_result`。
- **API契约**: `POST /api/app/credit/apply` 接口的内部处理逻辑发生断崖式变更，返回结构可能需要包含申请单号。
- **管理端**: 后台 BFF 及 `credit-risk-service` 需暴露新的管理端专属查询和审批修改接口。
