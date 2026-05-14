## Context

借款审批全链路建立在已经完成的授信体系之上。在授信阶段，用户已经获取了合法的 `UserCreditQuota` 循环可用额度和 `LoanContract` 授信协议。借款（Loan Application）环节作为真实的资金流出源头，对风险敏感度极高，需要引入独立的状态机、比授信更严苛的风控策略（加入实时行为校验）、以及事后产生借据（Receipt）并实时准确冻结额度的分布式流程。

## Goals / Non-Goals

**Goals:**
- 实现借款环节状态机：`INIT` -> `PENDING_RISK` -> `PENDING_FACE` -> `PENDING_MANUAL` -> `APPROVED` -> `REJECTED` 等。
- 实现针对借款环节的硬规则检查（增加实时设备、深夜特征等指标）。
- 实现 `UserCreditQuota` 在高并发场景下的安全额度扣减（基于乐观锁）。
- 拆分并持久化借据（Receipt）与还款计划（Repayment Plan）。

**Non-Goals:**
- 不在此变更中处理第三方支付通道的实际打款（放款）网络交互逻辑，仅到“生成借据与还款计划”为止。
- 借款逾期后的催收与计息系统设计不在本次范围内，本次仅负责生成初始还款计划。

## Decisions

### 1. 额度冻结机制设计
**决策**：使用基于数据库乐观锁（`version` 字段）的方式，在审批通过时直接对 `cf_user_credit_quota` 进行扣减操作。
**原因**：避免超卖现象发生。执行 `UPDATE cf_user_credit_quota SET available_amount = available_amount - #{amount}, used_amount = used_amount + #{amount}, version = version + 1 WHERE user_id = #{userId} AND available_amount >= #{amount} AND version = #{version}`。
**替代方案**：使用 Redis 分布式锁控制并发请求。但数据库层面的乐观锁实现更简洁且无需额外引入中间件依赖，对于借款场景并发度足够。

### 2. 借款风险路由阈值
**决策**：复用授信维度的 S1-S4 评分引擎，但调整 Nacos 下发的借款专用阈值配置。
**原因**：借款场景更关注资金损失风险，高危阈值应适当上调。如果在授信阶段评级为 LOW，但在借款前短期内命中“深夜发起”、“异地切换”特征，可由实时行为校验引擎直接否决或降级为 HIGH。

### 3. Agent 人工辅助审核的数据隔离
**决策**：借款人工审核共用 `cf_credit_review_queue` 审核队列，通过增加 `scene_type = 'LOAN'` 字段区分授信审核与借款审核。
**原因**：保证后台审核台代码的复用度，仅在内部标签和流转后续动作上做区分。Agent 生成的洞察同样以 JSON 形式存入队列记录的 `riskDetails` 字段。

## Risks / Trade-offs

- **[Risk] 额度并发扣减失败** → **Mitigation**: 业务层通过拦截并抛出“业务繁忙，请稍后重试”的错误，让用户手动重试。
- **[Risk] Agent 接口在借款风控评估时超时** → **Mitigation**: 采用 Feign fallback 策略，若 Agent 超时则立刻触发人工审核告警或使用安全底线兜底“暂不可知风险”，保障系统在 SLA 内响应。
