## 1. 数据库与实体层搭建

- [x] 1.1 在 MySQL 数据库中执行 DDL，创建 `cf_credit_application` 申请流水表，并建立相应的索引。
- [x] 1.2 在 `credit-risk-service` 中创建对应的 `CreditApplication` 实体类，映射数据库字段（包括 `applyAmount`, `suggestedAmount`, `status`, `auditReason` 等）。
- [x] 1.3 创建 `CreditApplicationMapper` 以及基础的 MyBatis-Plus Service 接口与实现类。

## 2. 授信申请核心链路重构

- [x] 2.1 重构 `CreditServiceImpl.applyCredit`，在调用 Agent 之前先落库一条状态为 `PENDING` 的 `CreditApplication` 记录。
- [x] 2.2 修改大模型调用逻辑的后续处理：不再直接生成 `CreditResult`，而是根据模型返回结果更新刚才插入的申请记录。
- [x] 2.3 当大模型返回拒绝时，更新记录状态为 `REJECTED` 并保存 `auditReason`。
- [x] 2.4 当大模型返回通过时，更新记录状态为 `APPROVED`，并提取 `suggestedAmount` 生成最终的 `CreditResult` 可用额度。

## 3. 后台管理查询功能

- [x] 3.1 在 `credit-risk-service` 中新增 `CreditAdminController`，暴露管理端 API。
- [x] 3.2 实现分页列表查询接口，支持传入 `startTime` 和 `endTime` 作为日期时间范围筛选条件，查询 `CreditApplication` 列表。
- [x] 3.3 完善查询服务层的业务逻辑并配置好相应的 MyBatis-Plus 分页插件。

## 4. 后台人工干预与审批兜底

- [x] 4.1 在 `CreditAdminController` 中新增人工强行审批通过接口：`POST /api/admin/credit/application/{id}/approve`。
- [x] 4.2 在 `CreditServiceImpl` 中实现人工强通逻辑：校验当前状态是否为 `REJECTED`，否则拦截；成功则更为 `APPROVED` 并追加备注。
- [x] 4.3 强行通过后，由服务生成与之对应的 `CreditResult` 给用户发放额度。
