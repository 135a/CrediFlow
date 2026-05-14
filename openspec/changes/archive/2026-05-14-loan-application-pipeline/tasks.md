## 1. 基础配置与实体映射

- [x] 1.1 `loan-application-service`: 扩展单笔借款申请实体（Loan Application），增加状态枚举（PENDING_RISK, PENDING_FACE, PENDING_MANUAL, APPROVED, REJECTED）。
- [x] 1.2 `loan-contract-service`: 创建独立借据（Loan Receipt）与还款计划分期明细（Repayment Plan）的实体类与 Mapper。
- [x] 1.3 `credit-risk-service`: 扩展 `CreditReviewQueue` 实体，新增 `sceneType` 字段用于隔离授信与借款。

## 2. 借款实时风控拦截与路由

- [x] 2.1 `loan-application-service`: 提供发起借款基础接口，并同步调用风控服务的借款评估 API。
- [x] 2.2 `credit-risk-service`: 新增借款实时规则校验管线，拦截高频、深夜异地、有在途逾期借款的申请。
- [x] 2.3 `credit-risk-service`: 复用授信四维模型，但加载借款专属高门槛阈值，计算风险等级（LOW, MEDIUM, HIGH）并返回路由结果。

## 3. 中高风险人脸与 Agent 介入

- [x] 3.1 `loan-application-service`: 处理 MEDIUM / HIGH 风险触发二次活体人脸的流程及异步回调，失败则直接拒贷。
- [x] 3.2 `credit-risk-service`: 处理 HIGH 风险且人脸成功的情况，将其投入 `cf_credit_review_queue` (标明 `sceneType='LOAN'`)。
- [x] 3.3 `agent-python`: 针对借款申请输出定制化的人工审核“三件套”洞察与建议。

## 4. 额度冻结与借据生成

- [x] 4.1 `loan-contract-service`: 提供生成正式全局借据及单号的业务接口。
- [x] 4.2 `loan-contract-service`: 按照等额本息/先息后本算法，将借据本金拆分为多期还款计划并落库保存。
- [x] 4.3 `credit-risk-service`: 提供高并发安全的额度扣减接口（基于 DB 乐观锁 `version`），在借据生成前/后执行 `availableAmount` 的安全扣除。
- [x] 4.4 端到端流程总装串联（发起借款 -> 风控 -> 审核 -> 生成借据与还款计划 -> 扣减额度）。
