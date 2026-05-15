# Capability: batch-job-endpoints

## Overview
在微服务架构中，定时任务的调度发起方统一收口在 Go 的 `batch-service` 中，由其以 HTTP 请求的形式呼叫各个业务节点的内部端点。该能力定义了 Java 侧为了响应这些批量调度而必须提供的端点规范，确保调度层的闭环不出错。

## Requirements

1. **post-loan-service: 逾期扫描端点**
   - 路径: `POST /api/internal/post-loan/overdue/scan`
   - 入参: 空体 或含 `triggerSource`。
   - 行为: 扫描逾期计划并更新状态（当前允许日志打印代替实体逻辑）。

2. **post-loan-service: 罚息计算端点**
   - 路径: `POST /api/internal/post-loan/penalty/calculate`
   - 入参: 包含 `calcDate` (日期格式) 和 `triggerSource` (字符串)。
   - 行为: 为已逾期的记录计算每日的罚息并累计（当前允许日志打印代替实体逻辑）。

3. **user-service: 还款提醒通知端点**
   - 路径: `POST /api/internal/user/notify/repayment-reminder`
   - 入参: 包含 `dueDate` (日期), `reminderType` (如 PRE_DUE) 和 `triggerSource`。
   - 行为: 向即将到期的用户发送通知提示还款（当前允许日志打印代替实体逻辑）。

4. **user-service: 批量通知推送端点**
   - 路径: `POST /api/internal/user/notify/batch-push`
   - 入参: 包含 `batchTime` (时间戳/字符串), `types` (字符串数组如 ["OVERDUE_WARN"]) 和 `triggerSource`。
   - 行为: 执行批量通知推送（当前允许日志打印代替实体逻辑）。
